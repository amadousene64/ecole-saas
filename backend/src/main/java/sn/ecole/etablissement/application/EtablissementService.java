package sn.ecole.etablissement.application;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.StatutEtablissement;
import sn.ecole.etablissement.domain.ControleImage;
import sn.ecole.etablissement.domain.ControleImage.ImageValidee;
import sn.ecole.etablissement.domain.Etablissement;
import sn.ecole.etablissement.domain.EtablissementRepository;
import sn.ecole.etablissement.domain.ImageEtablissement;
import sn.ecole.etablissement.domain.ImageEtablissementRepository;
import sn.ecole.etablissement.domain.StockageFichiers;
import sn.ecole.etablissement.domain.TypeImage;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.Identifiants;

/** Profil de l'école et ses images officielles (logo, cachet, signature). */
@Service
@Transactional
public class EtablissementService {

  /** Validité des URL signées : assez pour afficher la page, trop peu pour circuler. */
  public static final Duration VALIDITE_URL = Duration.ofMinutes(5);

  /** Le profil avec les URL signées des images que l'appelant a le droit de voir. */
  public record VueEtablissement(
      Etablissement etablissement, String logoUrl, String cachetUrl, String signatureUrl) {}

  public record Modification(
      String nom,
      String code,
      StatutEtablissement statut,
      String ia,
      String ief,
      String adresse,
      String telephone,
      String email,
      String fuseau,
      String devise) {}

  private final EtablissementRepository etablissements;
  private final ImageEtablissementRepository images;
  private final StockageFichiers stockage;
  private final JournalAudit journal;

  public EtablissementService(
      EtablissementRepository etablissements,
      ImageEtablissementRepository images,
      StockageFichiers stockage,
      JournalAudit journal) {
    this.etablissements = etablissements;
    this.images = images;
    this.stockage = stockage;
    this.journal = journal;
  }

  @PreAuthorize(Droits.LECTURE_TOUS)
  @Transactional(readOnly = true)
  public VueEtablissement lire() {
    return vue(charger());
  }

  @PreAuthorize(Droits.ECRITURE)
  public VueEtablissement modifier(int version, Modification m) {
    Etablissement e = charger();
    Erreurs.verifierVersion(version, e);
    e.modifier(
        m.nom(),
        m.code(),
        m.statut(),
        m.ia(),
        m.ief(),
        m.adresse(),
        m.telephone(),
        m.email(),
        m.fuseau(),
        m.devise());
    etablissements.save(e);
    journal.consigner("ETABLISSEMENT_MODIFIE", "Etablissement", e.getId(), Map.of());
    return vue(e);
  }

  /** Le logo est ouvert au secrétariat ; le cachet et la signature engagent le directeur seul. */
  @PreAuthorize("hasRole('DIRECTEUR') or (!#type.sensible() and hasRole('SECRETARIAT'))")
  public VueEtablissement deposerImage(TypeImage type, byte[] octets) {
    Etablissement e = charger();
    ImageValidee image = ControleImage.controler(octets, type);
    String cle =
        ContexteTenant.exiger()
            + "/etablissement/"
            + type.name().toLowerCase(Locale.ROOT)
            + "/"
            + Identifiants.uuidV7();
    stockage.deposer(cle, image.octets(), image.contentType());
    ApresTransaction.siAnnulee(() -> stockage.supprimer(cle));

    Optional<ImageEtablissement> existante = images.findByType(type);
    if (existante.isPresent()) {
      String ancienne = existante.get().getCle();
      existante.get().remplacer(cle, image.contentType(), image.octets().length);
      images.save(existante.get());
      ApresTransaction.siValidee(() -> stockage.supprimer(ancienne));
    } else {
      images.save(
          ImageEtablissement.deposer(type, cle, image.contentType(), image.octets().length));
    }
    journal.consigner("IMAGE_DEPOSEE", "Etablissement", e.getId(), Map.of("type", type.name()));
    return vue(e);
  }

  @PreAuthorize("hasRole('DIRECTEUR') or (!#type.sensible() and hasRole('SECRETARIAT'))")
  public VueEtablissement supprimerImage(TypeImage type) {
    Etablissement e = charger();
    ImageEtablissement image =
        images
            .findByType(type)
            .orElseThrow(() -> Erreurs.introuvable("IMAGE_INTROUVABLE", "Aucune image déposée."));
    String cle = image.getCle();
    image.retirer(Instant.now());
    images.save(image);
    ApresTransaction.siValidee(() -> stockage.supprimer(cle));
    journal.consigner("IMAGE_SUPPRIMEE", "Etablissement", e.getId(), Map.of("type", type.name()));
    return vue(e);
  }

  private Etablissement charger() {
    return etablissements
        .findFirstBy()
        .orElseThrow(
            () ->
                Erreurs.introuvable(
                    "ETABLISSEMENT_NON_CONFIGURE", "Le profil de l'école n'est pas configuré."));
  }

  private VueEtablissement vue(Etablissement e) {
    Map<TypeImage, String> urls = new LinkedHashMap<>();
    boolean directeur = Erreurs.estDirecteur();
    for (ImageEtablissement image : images.findAll()) {
      // Cachet et signature : jamais d'URL pour un autre rôle que le directeur.
      if (!image.getType().sensible() || directeur) {
        urls.put(image.getType(), stockage.urlSignee(image.getCle(), VALIDITE_URL).toString());
      }
    }
    return new VueEtablissement(
        e, urls.get(TypeImage.LOGO), urls.get(TypeImage.CACHET), urls.get(TypeImage.SIGNATURE));
  }
}
