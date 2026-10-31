package sn.ecole.inscription.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.CompteurMatricule;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.EleveRepository;
import sn.ecole.inscription.domain.FormatMatricule;
import sn.ecole.inscription.domain.IdentiteEleve;
import sn.ecole.inscription.domain.Matricule;
import sn.ecole.inscription.domain.RechercheEleves;
import sn.ecole.inscription.domain.Saisies;
import sn.ecole.inscription.domain.Sexe;
import sn.ecole.inscription.domain.StatutInscription;
import sn.ecole.shared.application.ApresTransaction;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.ControleImage;
import sn.ecole.shared.domain.ControleImage.ImageValidee;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.Identifiants;
import sn.ecole.shared.domain.PageCurseur;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.StockageFichiers;
import sn.ecole.shared.domain.TypeErreur;

/** Identité des élèves : création avec matricule, correction, photo, recherche. */
@Service
@Transactional
public class EleveService {

  /** Validité des URL signées : assez pour afficher la page, trop peu pour circuler. */
  public static final Duration VALIDITE_URL = Duration.ofMinutes(5);

  /** Taille maximale d'une photo d'élève (octets). */
  public static final int TAILLE_PHOTO_MAX = 1_000_000;

  static final ZoneId FUSEAU = ZoneId.of("Africa/Dakar");

  /** Saisie de l'identité, telle que reçue de l'API (validée par le domaine). */
  public record Saisie(
      String prenoms,
      String nom,
      Sexe sexe,
      LocalDate dateNaissance,
      String lieuNaissance,
      String nationalite,
      String identifiantNational) {

    @Override
    public String toString() {
      return "Saisie[***]";
    }
  }

  /** Un élève avec l'URL signée de sa photo (si l'appelant peut voir l'élève). */
  public record VueEleve(Eleve eleve, String photoUrl) {}

  private final EleveRepository eleves;
  private final RechercheEleves recherche;
  private final CompteurMatricule compteur;
  private final ParametresInscriptionService parametres;
  private final StockageFichiers stockage;
  private final JournalAudit journal;
  private final List<UtilisationEleve> utilisations;
  private final InscriptionService inscriptions;
  private final PieceService pieces;

  public EleveService(
      EleveRepository eleves,
      RechercheEleves recherche,
      CompteurMatricule compteur,
      ParametresInscriptionService parametres,
      StockageFichiers stockage,
      JournalAudit journal,
      List<UtilisationEleve> utilisations,
      InscriptionService inscriptions,
      PieceService pieces) {
    this.eleves = eleves;
    this.recherche = recherche;
    this.compteur = compteur;
    this.parametres = parametres;
    this.stockage = stockage;
    this.journal = journal;
    this.utilisations = List.copyOf(utilisations);
    this.inscriptions = inscriptions;
    this.pieces = pieces;
  }

  /**
   * @param terme nom, prénoms (sans tenir compte des accents) ou matricule exact ; null : tous
   * @param classeId élèves pré-inscrits ou inscrits dans cette classe ; null : tous
   * @param statut élèves dont l'inscription la plus récente a ce statut ; null : tous
   */
  @PreAuthorize(Droits.IDENTITE)
  @Transactional(readOnly = true)
  public PageCurseur<LigneEleve> rechercher(
      String terme, UUID classeId, StatutInscription statut, String curseur, Integer taille) {
    int n = PageCurseur.taille(taille);
    String apresNom = null;
    UUID apresId = null;
    if (curseur != null && !curseur.isBlank()) {
      String[] cle = PageCurseur.decoder(curseur, 2);
      apresNom = cle[0];
      try {
        apresId = UUID.fromString(cle[1]);
      } catch (IllegalArgumentException e) {
        throw Erreurs.invalide("CURSEUR_INVALIDE", "Curseur de page invalide.");
      }
    }
    String normalise = terme == null || terme.isBlank() ? null : Saisies.normaliser(terme.strip());
    if (normalise != null && normalise.length() > 100) {
      throw Erreurs.invalide("RECHERCHE_INVALIDE", "Recherche trop longue.");
    }
    // Le statut est de la scolarité : le comptable (identité seulement) ne le voit ni ne le filtre.
    boolean scolarite =
        Erreurs.aLeRole(RoleEcole.DIRECTEUR)
            || Erreurs.aLeRole(RoleEcole.SECRETARIAT)
            || Erreurs.aLeRole(RoleEcole.CENSEUR)
            || Erreurs.aLeRole(RoleEcole.SURVEILLANT);
    if (statut != null && !scolarite) {
      throw new ErreurMetier(
          "ACCES_REFUSE", TypeErreur.INTERDIT, "Le filtre par statut est réservé à la scolarité.");
    }
    List<Eleve> lus = recherche.rechercher(normalise, classeId, statut, apresNom, apresId, n + 1);
    PageCurseur<Eleve> page =
        PageCurseur.depuis(lus, n, e -> new String[] {e.getNomRecherche(), e.getId().toString()});
    List<UUID> ids = page.elements().stream().map(Eleve::getId).toList();
    Map<UUID, InscriptionService.Situation> situations = inscriptions.situations(ids);
    // Même règle par type que PieceService : les pièces exigées (acte, photo) sont lues par la
    // direction et le secrétariat ; le censeur ne lit que le certificat médical, donc pas l'état
    // ici.
    Map<UUID, DossierPieces> dossiers =
        Erreurs.aLeRole(RoleEcole.DIRECTEUR) || Erreurs.aLeRole(RoleEcole.SECRETARIAT)
            ? pieces.dossiers(ids)
            : Map.of();
    return page.map(
        e -> {
          InscriptionService.Situation s = situations.get(e.getId());
          return new LigneEleve(
              e,
              s == null ? null : s.classe(),
              s == null || !scolarite ? null : s.statut(),
              dossiers.get(e.getId()));
        });
  }

  @PreAuthorize(Droits.IDENTITE_ELEVE)
  @Transactional(readOnly = true)
  public VueEleve lire(UUID eleveId) {
    return vue(charger(eleveId));
  }

  /** Crée l'élève et lui attribue le prochain matricule selon le format de l'école. */
  @PreAuthorize(Droits.ECRITURE)
  public Eleve creer(Saisie saisie) {
    IdentiteEleve identite = identite(saisie);
    verifierIdentifiantNationalLibre(identite.identifiantNational(), null);
    Eleve eleve = eleves.save(Eleve.creer(attribuerMatricule(), identite));
    journal.consigner(
        "ELEVE_CREE", "Eleve", eleve.getId(), Map.of("matricule", eleve.getMatricule()));
    return eleve;
  }

  /**
   * Création par l'import (reprise de données). Matricule fourni : il doit être libre, même parmi
   * les élèves supprimés ; absent : attribué selon le format. Sans contrôle de droits : réservé à
   * l'import, qui les contrôle.
   */
  Eleve creerAvecMatricule(String matricule, IdentiteEleve identite) {
    String propre;
    if (matricule == null || matricule.isBlank()) {
      propre = attribuerMatricule();
    } else {
      propre = Matricule.verifier(matricule);
      if (eleves.matriculeExiste(propre)) {
        throw Erreurs.conflit("MATRICULE_EXISTANT", "Ce matricule est déjà attribué.");
      }
    }
    verifierIdentifiantNationalLibre(identite.identifiantNational(), null);
    Eleve eleve = eleves.save(Eleve.creer(propre, identite));
    journal.consigner(
        "ELEVE_CREE", "Eleve", eleve.getId(), Map.of("matricule", propre, "par", "IMPORT"));
    return eleve;
  }

  @PreAuthorize(Droits.ECRITURE)
  public VueEleve modifier(UUID eleveId, int version, Saisie saisie) {
    Eleve eleve = charger(eleveId);
    Erreurs.verifierVersion(version, eleve);
    IdentiteEleve identite = identite(saisie);
    verifierIdentifiantNationalLibre(identite.identifiantNational(), eleveId);
    eleve.corriger(identite);
    Eleve enregistre = eleves.save(eleve);
    // Les valeurs ne sont pas journalisées (données personnelles) : seulement le fait.
    journal.consigner("ELEVE_MODIFIE", "Eleve", eleveId, Map.of());
    return vue(enregistre);
  }

  /** Seul un élève créé par erreur, sans aucune donnée rattachée, peut être supprimé. */
  @PreAuthorize(Droits.DIRECTION)
  public void supprimer(UUID eleveId) {
    Eleve eleve = charger(eleveId);
    if (utilisations.stream().anyMatch(u -> u.eleveUtilise(eleveId))) {
      throw Erreurs.conflit(
          "ELEVE_UTILISE",
          "Cet élève a une inscription ou des responsables : il ne peut pas être supprimé.");
    }
    String photo = eleve.retirerPhoto();
    eleve.supprimer(Instant.now());
    eleves.save(eleve);
    if (photo != null) {
      ApresTransaction.siValidee(() -> stockage.supprimer(photo));
    }
    journal.consigner(
        "ELEVE_SUPPRIME", "Eleve", eleveId, Map.of("matricule", eleve.getMatricule()));
  }

  /** Photo d'identité : mêmes contrôles que le logo (type réel, dimensions, ré-encodage). */
  @PreAuthorize(Droits.ECRITURE)
  public VueEleve deposerPhoto(UUID eleveId, byte[] octets) {
    Eleve eleve = charger(eleveId);
    ImageValidee image = ControleImage.controler(octets, TAILLE_PHOTO_MAX);
    String cle = ContexteTenant.exiger() + "/eleves/" + eleveId + "/photo/" + Identifiants.uuidV7();
    stockage.deposer(cle, image.octets(), image.contentType());
    ApresTransaction.siAnnulee(() -> stockage.supprimer(cle));
    String ancienne = eleve.changerPhoto(cle, image.contentType());
    Eleve enregistre = eleves.save(eleve);
    if (ancienne != null) {
      ApresTransaction.siValidee(() -> stockage.supprimer(ancienne));
    }
    journal.consigner("PHOTO_ELEVE_DEPOSEE", "Eleve", eleveId, Map.of());
    return vue(enregistre);
  }

  @PreAuthorize(Droits.ECRITURE)
  public VueEleve supprimerPhoto(UUID eleveId) {
    Eleve eleve = charger(eleveId);
    String ancienne = eleve.retirerPhoto();
    if (ancienne == null) {
      throw Erreurs.introuvable("PHOTO_INTROUVABLE", "Aucune photo déposée.");
    }
    Eleve enregistre = eleves.save(eleve);
    ApresTransaction.siValidee(() -> stockage.supprimer(ancienne));
    journal.consigner("PHOTO_ELEVE_SUPPRIMEE", "Eleve", eleveId, Map.of());
    return vue(enregistre);
  }

  Eleve charger(UUID eleveId) {
    return eleves.findById(eleveId).orElseThrow(Erreurs::eleveIntrouvable);
  }

  /** L'élève connecté (compte relié par code d'activation). */
  @PreAuthorize("hasRole('ELEVE')")
  @Transactional(readOnly = true)
  public VueEleve moi() {
    return vue(
        eleves
            .findByUtilisateurId(Erreurs.utilisateurCourant())
            .orElseThrow(Erreurs::eleveIntrouvable));
  }

  Optional<Eleve> parUtilisateur(UUID utilisateurId) {
    return eleves.findByUtilisateurId(utilisateurId);
  }

  static IdentiteEleve identite(Saisie s) {
    return IdentiteEleve.valider(
        s.prenoms(),
        s.nom(),
        s.sexe(),
        s.dateNaissance(),
        s.lieuNaissance(),
        s.nationalite(),
        s.identifiantNational(),
        LocalDate.now(FUSEAU));
  }

  private VueEleve vue(Eleve eleve) {
    String url =
        eleve.getPhotoCle() == null
            ? null
            : stockage.urlSignee(eleve.getPhotoCle(), VALIDITE_URL).toString();
    return new VueEleve(eleve, url);
  }

  /**
   * Prochain numéro du compteur ; saute les matricules déjà pris (par exemple importés à la main
   * avec le même format).
   */
  private String attribuerMatricule() {
    FormatMatricule format = parametres.courants().format();
    int annee = LocalDate.now(FUSEAU).getYear();
    String cle = format.cleSequence(annee);
    for (int essai = 0; essai < 1000; essai++) {
      String matricule = format.generer(annee, compteur.suivant(cle));
      if (!eleves.matriculeExiste(matricule)) {
        return matricule;
      }
    }
    throw Erreurs.conflit(
        "SEQUENCE_MATRICULE_EPUISEE",
        "Impossible de trouver un matricule libre : revoyez le format.");
  }

  private void verifierIdentifiantNationalLibre(String identifiant, UUID sauf) {
    if (identifiant == null) {
      return;
    }
    eleves
        .findByIdentifiantNational(identifiant)
        .filter(autre -> !autre.getId().equals(sauf))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "IDENTIFIANT_NATIONAL_EXISTANT",
                  "Cet identifiant national est déjà celui d'un autre élève (matricule "
                      + autre.getMatricule()
                      + ").");
            });
  }
}
