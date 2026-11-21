package sn.ecole.etablissement.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.Etablissement;
import sn.ecole.etablissement.domain.EtablissementRepository;
import sn.ecole.etablissement.domain.ImageEtablissement;
import sn.ecole.etablissement.domain.ImageEtablissementRepository;
import sn.ecole.etablissement.domain.TypeImage;
import sn.ecole.shared.application.EmpreinteSecrets;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.LimiteurDebit;
import sn.ecole.shared.domain.StockageFichiers;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Marque de l'école pour les pages publiques (connexion Keycloak) : nom, ville, phrase d'accueil et
 * LOGO. Aucune authentification : c'est public par nature, donc volontairement limité — le cachet
 * et la signature ne sont jamais lus par ce service (le type d'image n'est pas un paramètre : il
 * vaut {@link TypeImage#LOGO}, en dur). Les appels sont limités par adresse IP, et la marque est
 * gardée quelques minutes en mémoire (l'école ne change pas de nom à chaque connexion).
 *
 * <p>L'école vient de {@code ResolveurEcolePublic} (sous-domaine) ; sans école, la marque est
 * neutre, jamais une erreur : on ne révèle pas quelles écoles existent.
 */
@Service
// Lecture seule, sans transaction propre : chaque lecture ouvre la sienne pour l'école désignée.
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class MarquePubliqueService {

  static final int APPELS_PAR_MINUTE = 120;
  static final Duration FENETRE = Duration.ofMinutes(1);
  static final Duration DUREE_CACHE = Duration.ofMinutes(5);
  private static final int TAILLE_CACHE_MAX = 1000;

  /**
   * @param logoVersion identifiant de la version du logo (change à chaque remplacement), ou null
   */
  public record Marque(String nom, String ville, String phraseAccueil, String logoVersion) {

    public static final Marque NEUTRE = new Marque(null, null, null, null);

    public boolean neutre() {
      return nom == null;
    }
  }

  public record Logo(byte[] octets, String contentType, String version) {}

  private record Entree(Marque marque, Instant expire) {}

  private final ExecutionPourTenant execution;
  private final EtablissementRepository etablissements;
  private final ImageEtablissementRepository images;
  private final StockageFichiers stockage;
  private final LimiteurDebit limiteur;
  private final EmpreinteSecrets empreintes;
  private final Map<UUID, Entree> cache = new ConcurrentHashMap<>();

  public MarquePubliqueService(
      ExecutionPourTenant execution,
      EtablissementRepository etablissements,
      ImageEtablissementRepository images,
      StockageFichiers stockage,
      LimiteurDebit limiteur,
      EmpreinteSecrets empreintes) {
    this.execution = execution;
    this.etablissements = etablissements;
    this.images = images;
    this.stockage = stockage;
    this.limiteur = limiteur;
    this.empreintes = empreintes;
  }

  /** Public (sans jeton) : l'école n'est pas un utilisateur, la limitation de débit protège. */
  @PreAuthorize("permitAll()")
  public Marque marque(Supplier<Optional<UUID>> ecole, String adresseIp) {
    // La limite d'abord : un appel refusé ne coûte aucune lecture de la table des écoles.
    limiter(adresseIp);
    return ecole.get().map(this::marqueDe).orElse(Marque.NEUTRE);
  }

  /** Le logo seulement (jamais le cachet ni la signature) ; vide si l'école n'en a pas. */
  @PreAuthorize("permitAll()")
  public Optional<Logo> logo(Supplier<Optional<UUID>> ecole, String adresseIp) {
    limiter(adresseIp);
    Optional<UUID> designee = ecole.get();
    if (designee.isEmpty()) {
      return Optional.empty();
    }
    return execution.lire(
        designee.get(),
        () ->
            images
                .findByType(TypeImage.LOGO)
                .map(
                    image ->
                        new Logo(
                            stockage.lire(image.getCle()),
                            image.getContentType(),
                            version(image))));
  }

  /**
   * Oublie la marque gardée de l'école (profil ou logo modifié). Interne au module : appelé par les
   * services du profil, déjà autorisés. Une autre instance garde sa copie jusqu'à {@link
   * #DUREE_CACHE}.
   */
  void invalider(UUID ecole) {
    cache.remove(ecole);
  }

  private Marque marqueDe(UUID ecole) {
    Instant maintenant = Instant.now();
    Entree gardee = cache.get(ecole);
    if (gardee != null && gardee.expire().isAfter(maintenant)) {
      return gardee.marque();
    }
    Marque lue =
        execution.lire(
            ecole,
            () ->
                etablissements
                    .findFirstBy()
                    .map(
                        (Etablissement e) ->
                            new Marque(
                                e.getNom(),
                                e.getVille(),
                                e.getPhraseAccueil(),
                                images.findByType(TypeImage.LOGO).map(this::version).orElse(null)))
                    .orElse(Marque.NEUTRE));
    if (cache.size() >= TAILLE_CACHE_MAX) {
      cache.clear();
    }
    cache.put(ecole, new Entree(lue, maintenant.plus(DUREE_CACHE)));
    return lue;
  }

  /** Change quand le logo est remplacé (clé d'objet unique par dépôt) : sert d'ETag. */
  private String version(ImageEtablissement image) {
    return HexFormat.of().formatHex(empreintes.empreinte(image.getCle(), "marque|logo"), 0, 8);
  }

  private void limiter(String adresseIp) {
    String cle =
        "marque:ip:"
            + HexFormat.of()
                .formatHex(empreintes.empreinte(String.valueOf(adresseIp), "limite|ip"), 0, 16);
    if (!limiteur.autoriser(cle, APPELS_PAR_MINUTE, FENETRE)) {
      throw new ErreurMetier(
          "TROP_DE_TENTATIVES",
          TypeErreur.TROP_DE_TENTATIVES,
          "Trop de demandes. Réessayez dans une minute.");
    }
  }
}
