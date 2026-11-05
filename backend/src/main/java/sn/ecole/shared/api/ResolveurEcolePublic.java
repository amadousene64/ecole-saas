package sn.ecole.shared.api;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

/**
 * École désignée par une requête SANS authentification (pages de connexion Keycloak, qui affichent
 * la marque de l'école). Aucune appartenance n'est vérifiée : ce que l'on peut apprendre de l'école
 * est public par nature (nom, ville, phrase d'accueil, logo), et rien d'autre ne passe par ici.
 *
 * <ul>
 *   <li>mode {@code SOUS_DOMAINE} : l'école est celle du sous-domaine de l'hôte demandé ({@code
 *       lycee-x.monapp.sn}) ; un hôte commun, un hôte inconnu ou une école inactive ne désignent
 *       personne (marque neutre, sans distinction : pas d'énumération des écoles) ;
 *   <li>mode {@code ENTETE} (développement) : l'école peut aussi être donnée par son identifiant,
 *       ou être l'école par défaut du poste.
 * </ul>
 */
@Component
public class ResolveurEcolePublic {

  private static final Pattern LIBELLE_DNS =
      Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

  private final TenantProperties proprietes;
  private final TenantRepository tenants;
  private final Optional<UUID> ecoleParDefaut;

  ResolveurEcolePublic(
      TenantProperties proprietes,
      TenantRepository tenants,
      @org.springframework.beans.factory.annotation.Value("${ecole.marque.ecole-par-defaut:}")
          String ecoleParDefaut) {
    this.proprietes = proprietes;
    this.tenants = tenants;
    this.ecoleParDefaut =
        ecoleParDefaut == null || ecoleParDefaut.isBlank()
            ? Optional.empty()
            : Optional.of(UUID.fromString(ecoleParDefaut.strip()));
  }

  /**
   * @param hote hôte (avec ou sans port) de l'application vers laquelle Keycloak redirige
   * @param ecole identifiant d'école, accepté en développement seulement
   */
  public Optional<UUID> resoudre(String hote, UUID ecole) {
    Optional<UUID> parHote = parSousDomaine(hote);
    if (parHote.isPresent()) {
      return parHote;
    }
    if (proprietes.resolution() == TenantProperties.Resolution.ENTETE) {
      return actif(ecole != null ? Optional.of(ecole) : ecoleParDefaut);
    }
    return Optional.empty();
  }

  private Optional<UUID> parSousDomaine(String hote) {
    if (hote == null
        || hote.isBlank()
        || proprietes.resolution() != TenantProperties.Resolution.SOUS_DOMAINE) {
      return Optional.empty();
    }
    String nom = sansPort(hote.strip().toLowerCase(Locale.ROOT));
    if (proprietes.estHoteCommun(nom)) {
      return Optional.empty();
    }
    String suffixe = "." + proprietes.domaineRacine().toLowerCase(Locale.ROOT);
    if (!nom.endsWith(suffixe)) {
      return Optional.empty();
    }
    String sousDomaine = nom.substring(0, nom.length() - suffixe.length());
    if (!LIBELLE_DNS.matcher(sousDomaine).matches()) {
      return Optional.empty();
    }
    return actif(tenants.findBySousDomaine(sousDomaine).map(Tenant::getId));
  }

  private Optional<UUID> actif(Optional<UUID> ecole) {
    return ecole.filter(id -> tenants.findById(id).map(Tenant::isActif).orElse(false));
  }

  private static String sansPort(String hote) {
    int deuxPoints = hote.lastIndexOf(':');
    return deuxPoints > 0 && hote.indexOf(']') < 0 ? hote.substring(0, deuxPoints) : hote;
  }
}
