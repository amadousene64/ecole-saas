package sn.ecole.inscription.application;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.DossierSensible;
import sn.ecole.inscription.domain.DossierSensible.Champ;
import sn.ecole.inscription.domain.DossierSensibleRepository;
import sn.ecole.inscription.domain.Saisies;
import sn.ecole.shared.application.ChiffrementDonnees;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ContexteTenant;

/**
 * Données sensibles d'un élève : santé, allergies, situation familiale.
 *
 * <ul>
 *   <li>chiffrées en colonne (AES-GCM, clé hors base), le contexte liant chaque valeur à son école,
 *       son élève et son champ ;
 *   <li>lisibles par le DIRECTEUR et le CENSEUR seulement, chaque lecture étant journalisée dans la
 *       même transaction (pas de lecture sans trace) ;
 *   <li>écrites par le DIRECTEUR ou le SECRETARIAT, qui ne peut pas les relire ;
 *   <li>jamais journalisées en clair : le journal ne garde que le nom des champs.
 * </ul>
 */
@Service
@Transactional
public class DossierSensibleService {

  public static final String LECTURE = "DONNEES_SENSIBLES_LUES";
  public static final String ECRITURE = "DONNEES_SENSIBLES_MODIFIEES";

  /** Texte clair, {@code null} si non renseigné. */
  public record Contenu(String sante, String allergies, String situationFamiliale, int version) {

    @Override
    public String toString() {
      return "Contenu[***]";
    }
  }

  /** Ce qui est renseigné, sans le contenu : pour le secrétariat. */
  public record Etat(boolean sante, boolean allergies, boolean situationFamiliale, int version) {}

  /**
   * Modification partielle : {@code null} laisse le champ inchangé, une chaîne vide l'efface.
   * Permet au secrétariat de compléter un champ sans connaître les autres.
   */
  public record Modification(String sante, String allergies, String situationFamiliale) {

    @Override
    public String toString() {
      return "Modification[***]";
    }

    String valeur(Champ champ) {
      return switch (champ) {
        case SANTE -> sante;
        case ALLERGIES -> allergies;
        case SITUATION_FAMILIALE -> situationFamiliale;
      };
    }
  }

  private final DossierSensibleRepository dossiers;
  private final EleveService eleves;
  private final ChiffrementDonnees chiffrement;
  private final JournalAudit journal;

  public DossierSensibleService(
      DossierSensibleRepository dossiers,
      EleveService eleves,
      ChiffrementDonnees chiffrement,
      JournalAudit journal) {
    this.dossiers = dossiers;
    this.eleves = eleves;
    this.chiffrement = chiffrement;
    this.journal = journal;
  }

  /** Lecture en clair, journalisée (même si rien n'est renseigné : la consultation compte). */
  @PreAuthorize(Droits.SENSIBLE_LECTURE)
  public Contenu lire(UUID eleveId) {
    eleves.charger(eleveId);
    DossierSensible dossier =
        dossiers.findByEleveId(eleveId).orElseGet(() -> DossierSensible.pour(eleveId));
    Map<Champ, String> clairs = new EnumMap<>(Champ.class);
    List<String> lus = new ArrayList<>();
    for (Champ champ : Champ.values()) {
      String clair = chiffrement.dechiffrer(dossier.chiffre(champ), contexte(eleveId, champ));
      clairs.put(champ, clair);
      if (clair != null) {
        lus.add(champ.name());
      }
    }
    journal.consigner(LECTURE, "Eleve", eleveId, Map.of("champs", lus));
    return new Contenu(
        clairs.get(Champ.SANTE),
        clairs.get(Champ.ALLERGIES),
        clairs.get(Champ.SITUATION_FAMILIALE),
        version(dossier));
  }

  @PreAuthorize(Droits.SENSIBLE_ETAT)
  @Transactional(readOnly = true)
  public Etat etat(UUID eleveId) {
    eleves.charger(eleveId);
    DossierSensible dossier =
        dossiers.findByEleveId(eleveId).orElseGet(() -> DossierSensible.pour(eleveId));
    return new Etat(
        dossier.renseigne(Champ.SANTE),
        dossier.renseigne(Champ.ALLERGIES),
        dossier.renseigne(Champ.SITUATION_FAMILIALE),
        version(dossier));
  }

  /**
   * Écriture partielle (If-Match : version lue par {@link #etat} ou {@link #lire}). Renvoie l'état,
   * jamais le contenu : le secrétariat n'a pas à relire ce qu'il vient d'écrire.
   */
  @PreAuthorize(Droits.SENSIBLE_ECRITURE)
  public Etat modifier(UUID eleveId, int version, Modification modification) {
    eleves.charger(eleveId);
    DossierSensible dossier =
        dossiers.findByEleveId(eleveId).orElseGet(() -> DossierSensible.pour(eleveId));
    if (version != version(dossier)) {
      throw Erreurs.conflit(
          "CONFLIT_VERSION",
          "La donnée a été modifiée entre-temps : rechargez-la puis recommencez.");
    }
    List<String> modifies = new ArrayList<>();
    for (Champ champ : Champ.values()) {
      String valeur = modification.valeur(champ);
      if (valeur == null) {
        continue;
      }
      String propre = Saisies.optionnel(valeur, "Ce champ", champ.longueurMax());
      dossier.remplacer(champ, chiffrement.chiffrer(propre, contexte(eleveId, champ)));
      modifies.add(champ.name());
    }
    if (modifies.isEmpty()) {
      return etat(dossier);
    }
    DossierSensible enregistre = dossiers.saveAndFlush(dossier);
    journal.consigner(ECRITURE, "Eleve", eleveId, Map.of("champs", modifies));
    return etat(enregistre);
  }

  /** École, élève et champ : un chiffré recopié ailleurs ne se déchiffre pas. */
  static String contexte(UUID eleveId, Champ champ) {
    return ContexteTenant.exiger() + "|eleve|" + eleveId + "|" + champ.name();
  }

  private static int version(DossierSensible dossier) {
    return dossier.getVersion() == null ? 0 : dossier.getVersion() + 1;
  }

  private static Etat etat(DossierSensible d) {
    return new Etat(
        d.renseigne(Champ.SANTE),
        d.renseigne(Champ.ALLERGIES),
        d.renseigne(Champ.SITUATION_FAMILIALE),
        version(d));
  }
}
