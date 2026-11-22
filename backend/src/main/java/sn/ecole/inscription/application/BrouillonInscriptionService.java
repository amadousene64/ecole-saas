package sn.ecole.inscription.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.inscription.domain.BrouillonInscription;
import sn.ecole.inscription.domain.BrouillonInscriptionRepository;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.inscription.domain.Responsable;
import sn.ecole.inscription.domain.Sexe;
import sn.ecole.inscription.domain.StatutInscription;
import sn.ecole.inscription.domain.TypeLien;
import sn.ecole.shared.application.ChiffrementDonnees;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.PageCurseur;
import sn.ecole.shared.domain.TypeErreur;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Assistant d'inscription en 5 étapes (Élève, Responsables, Scolarité, Pièces, Vérification), avec
 * un brouillon enregistré côté serveur à chaque étape.
 *
 * <ul>
 *   <li>Le contenu saisi est un document JSON CHIFFRÉ (AES-GCM, clé hors base) ; le contexte lie le
 *       chiffré à l'école et au brouillon. Jamais journalisé en clair.
 *   <li>Tout est facultatif pendant la saisie ; tout est contrôlé à la finalisation, qui crée en
 *       une transaction l'élève, ses responsables et leurs liens, l'inscription, et rattache les
 *       pièces déposées au dossier. Le brouillon est alors clos (contenu effacé).
 *   <li>Direction et secrétariat : les brouillons sont ceux de l'école (travail d'équipe), pas
 *       seulement ceux de leur auteur.
 *   <li>Verrou optimiste (version) à chaque écriture : deux onglets ne s'écrasent pas, et un
 *       brouillon ne se finalise qu'une fois.
 * </ul>
 */
@Service
@Transactional
public class BrouillonInscriptionService {

  private static final Logger LOG = LoggerFactory.getLogger(BrouillonInscriptionService.class);

  /** Taille maximale du contenu (JSON) : quelques responsables, jamais un fichier. */
  static final int TAILLE_MAX = 20_000;

  static final int RESPONSABLES_MAX = 4;

  /** Contenu du brouillon : tout est facultatif tant que le brouillon n'est pas finalisé. */
  public record Contenu(
      EleveSaisi eleve, List<ResponsableSaisi> responsables, Scolarite scolarite) {

    public Contenu {
      responsables = responsables == null ? List.of() : List.copyOf(responsables);
    }

    static Contenu vide() {
      return new Contenu(null, List.of(), null);
    }

    @Override
    public String toString() {
      return "Contenu[***]";
    }
  }

  public record EleveSaisi(
      String prenoms,
      String nom,
      Sexe sexe,
      LocalDate dateNaissance,
      String lieuNaissance,
      String nationalite,
      String identifiantNational) {

    @Override
    public String toString() {
      return "EleveSaisi[***]";
    }
  }

  /**
   * Un responsable : existant ({@code responsableId}, trouvé par son téléphone) ou à créer (les
   * autres champs d'identité).
   */
  public record ResponsableSaisi(
      UUID responsableId,
      String prenoms,
      String nom,
      String telephone,
      String email,
      TypeLien type,
      boolean responsableLegal,
      boolean payeur,
      boolean contactUrgence,
      boolean autoriseRecuperer,
      boolean recoitNotifications) {

    @Override
    public String toString() {
      return "ResponsableSaisi[***]";
    }
  }

  public record Scolarite(UUID classeId, List<UUID> options, boolean redoublant) {

    public Scolarite {
      options = options == null ? List.of() : List.copyOf(options);
    }
  }

  /** Brouillon déchiffré. */
  public record Vue(
      UUID id,
      UUID anneeId,
      int etape,
      Contenu contenu,
      UUID auteurId,
      Instant modifieLe,
      Instant expireLe,
      int version) {}

  /** Ligne de la liste « Brouillons en cours » : le nom seul, pour reprendre la saisie. */
  public record Resume(
      UUID id,
      String libelle,
      int etape,
      UUID auteurId,
      Instant modifieLe,
      Instant expireLe,
      int version) {}

  /**
   * Résultat de la finalisation : l'élève créé, les avertissements (capacité…) et le code
   * d'activation demandé (nul sinon ; masqué dans {@code toString}).
   */
  public record Finalisation(
      UUID eleveId,
      UUID inscriptionId,
      List<String> avertissements,
      ActivationService.CodeEmis code) {}

  private final BrouillonInscriptionRepository brouillons;
  private final ReferentielScolaire referentiel;
  private final EleveService eleves;
  private final ResponsableService responsables;
  private final LienParentalService liens;
  private final InscriptionService inscriptions;
  private final PieceService pieces;
  private final ActivationService activation;
  private final ChiffrementDonnees chiffrement;
  private final JsonMapper json;
  private final JournalAudit journal;

  public BrouillonInscriptionService(
      BrouillonInscriptionRepository brouillons,
      ReferentielScolaire referentiel,
      EleveService eleves,
      ResponsableService responsables,
      LienParentalService liens,
      InscriptionService inscriptions,
      PieceService pieces,
      ActivationService activation,
      ChiffrementDonnees chiffrement,
      JsonMapper json,
      JournalAudit journal) {
    this.brouillons = brouillons;
    this.referentiel = referentiel;
    this.eleves = eleves;
    this.responsables = responsables;
    this.liens = liens;
    this.inscriptions = inscriptions;
    this.pieces = pieces;
    this.activation = activation;
    this.chiffrement = chiffrement;
    this.json = json;
    this.journal = journal;
  }

  /**
   * Ouvre un brouillon vide pour l'année donnée, ou l'année en cours si {@code anneeId} est nul
   * (inscriptions de la rentrée suivante : année en préparation).
   */
  @PreAuthorize(Droits.ECRITURE)
  public Vue ouvrir(UUID anneeId) {
    UUID annee = anneeId != null ? anneeOuverte(anneeId) : anneeEnCours();
    UUID auteur = Erreurs.utilisateurCourant();
    // Verrou par auteur : des ouvertures simultanées ne dépassent pas le plafond.
    brouillons.verrouillerAuteur(auteur);
    if (brouillons.countByAuteurId(auteur) >= BrouillonInscription.MAX_PAR_AUTEUR) {
      throw Erreurs.conflit(
          "BROUILLONS_TROP_NOMBREUX",
          "Vous avez déjà "
              + BrouillonInscription.MAX_PAR_AUTEUR
              + " inscriptions en cours. Terminez-en ou abandonnez-en une (liste des élèves,"
              + " « Inscriptions en cours ») avant d'en commencer une autre.");
    }
    BrouillonInscription b = BrouillonInscription.ouvrir(annee, auteur);
    b.remplacer(chiffrer(b.getId(), Contenu.vide()), 1);
    b = brouillons.saveAndFlush(b);
    journal.consigner("BROUILLON_OUVERT", "BrouillonInscription", b.getId(), Map.of());
    return vue(b, Contenu.vide());
  }

  @PreAuthorize(Droits.ECRITURE)
  @Transactional(readOnly = true)
  public PageCurseur<Resume> lister(String curseur, Integer taille) {
    int n = PageCurseur.taille(taille);
    Instant apresModifieLe = null;
    UUID apresId = null;
    if (curseur != null && !curseur.isBlank()) {
      // Clé opaque : date de modification et identifiant du dernier brouillon, jamais un nom.
      String[] cle = PageCurseur.decoder(curseur, 2);
      try {
        apresModifieLe = Instant.parse(cle[0]);
        apresId = UUID.fromString(cle[1]);
      } catch (RuntimeException e) {
        throw Erreurs.invalide("CURSEUR_INVALIDE", "Curseur de page invalide.");
      }
    }
    PageCurseur<BrouillonInscription> page =
        PageCurseur.depuis(
            brouillons.page(apresModifieLe, apresId, n + 1),
            n,
            b -> new String[] {b.getModifieLe().toString(), b.getId().toString()});
    return page.map(this::resume);
  }

  private Resume resume(BrouillonInscription b) {
    String libelle;
    try {
      EleveSaisi e = dechiffrer(b).eleve();
      libelle = e == null ? "" : (nonVide(e.prenoms()) + " " + nonVide(e.nom())).strip();
      libelle = libelle.isEmpty() ? "Élève sans nom" : libelle;
    } catch (ErreurMetier | IllegalStateException illisible) {
      // JSON illisible ou déchiffrement impossible (clé retirée…) : le brouillon reste listé, pour
      // pouvoir l'abandonner. Ni la cause ni son message dans le journal.
      LOG.warn("Brouillon {} illisible ({})", b.getId(), illisible.getClass().getSimpleName());
      libelle = "Brouillon illisible";
    }
    return new Resume(
        b.getId(),
        libelle,
        b.getEtape(),
        b.getAuteurId(),
        b.getModifieLe(),
        b.expireLe(),
        b.getVersion());
  }

  @PreAuthorize(Droits.ECRITURE)
  @Transactional(readOnly = true)
  public Vue lire(UUID id) {
    BrouillonInscription b = charger(id);
    return vue(b, dechiffrer(b));
  }

  /** Enregistre l'étape courante (contenu complet, jamais un fragment) ; If-Match obligatoire. */
  @PreAuthorize(Droits.ECRITURE)
  public Vue enregistrer(UUID id, int version, int etape, Contenu contenu) {
    BrouillonInscription b = charger(id);
    Erreurs.verifierVersion(version, b);
    Contenu propre = Objects.requireNonNullElseGet(contenu, Contenu::vide);
    if (propre.responsables().size() > RESPONSABLES_MAX) {
      throw Erreurs.invalide(
          "TROP_DE_RESPONSABLES", RESPONSABLES_MAX + " responsables au plus par élève.");
    }
    b.remplacer(chiffrer(id, propre), etape);
    b = brouillons.saveAndFlush(b);
    return vue(b, propre);
  }

  @PreAuthorize(Droits.ECRITURE)
  public void abandonner(UUID id, int version) {
    BrouillonInscription b = verrouiller(id);
    Erreurs.verifierVersion(version, b);
    pieces.abandonnerDuBrouillon(id);
    b.clore(Instant.now());
    brouillons.saveAndFlush(b);
    journal.consigner("BROUILLON_ABANDONNE", "BrouillonInscription", id, Map.of());
  }

  /**
   * Crée l'élève, ses responsables et leurs liens, l'inscription ; rattache les pièces ; clôt le
   * brouillon. Tout ou rien : une erreur (classe complète en politique bloquante, doublon…) annule
   * tout et le brouillon reste intact.
   *
   * <p>{@code codePour} : rang du responsable (dans le brouillon) pour qui émettre un code
   * d'activation, comme depuis la fiche. Un responsable déjà relié à un compte n'en reçoit pas :
   * l'inscription se fait, avec un avertissement.
   *
   * @param statut INSCRIT ou PRE_INSCRIT
   * @param codePour rang du responsable, ou nul
   */
  @PreAuthorize(Droits.ECRITURE)
  public Finalisation finaliser(UUID id, int version, StatutInscription statut, Integer codePour) {
    BrouillonInscription b = verrouiller(id);
    Erreurs.verifierVersion(version, b);
    if (statut != StatutInscription.INSCRIT && statut != StatutInscription.PRE_INSCRIT) {
      throw Erreurs.invalide("STATUT_INVALIDE", "Statut attendu : INSCRIT ou PRE_INSCRIT.");
    }
    Contenu c = dechiffrer(b);
    exigerComplet(c);
    if (codePour != null && (codePour < 0 || codePour >= c.responsables().size())) {
      throw Erreurs.invalide(
          "RESPONSABLE_INTROUVABLE", "Le code d'activation vise un responsable absent.");
    }

    EleveSaisi e = c.eleve();
    UUID eleveId =
        eleves
            .creer(
                new EleveService.Saisie(
                    e.prenoms(),
                    e.nom(),
                    e.sexe(),
                    e.dateNaissance(),
                    e.lieuNaissance(),
                    e.nationalite(),
                    e.identifiantNational()))
            .getId();
    List<Responsable> lies = new ArrayList<>();
    for (ResponsableSaisi r : c.responsables()) {
      Responsable responsable =
          r.responsableId() != null
              ? responsables.lire(r.responsableId())
              : responsables.creer(
                  new ResponsableService.Saisie(
                      r.prenoms(), r.nom(), r.telephone(), r.email(), null));
      lies.add(responsable);
      liens.ajouter(
          eleveId,
          responsable.getId(),
          r.type(),
          new LienParental.Droits(
              r.responsableLegal(),
              r.payeur(),
              r.contactUrgence(),
              r.autoriseRecuperer(),
              r.recoitNotifications()));
    }
    Scolarite s = c.scolarite();
    InscriptionService.VueInscription inscription =
        inscriptions.inscrire(eleveId, s.classeId(), statut, s.redoublant(), s.options());
    pieces.rattacherAuDossier(id, eleveId);
    b.clore(Instant.now());
    brouillons.saveAndFlush(b);
    journal.consigner(
        "BROUILLON_FINALISE",
        "BrouillonInscription",
        id,
        Map.of("eleve", eleveId, "statut", statut));
    List<String> avertissements = new ArrayList<>(inscription.avertissements());
    ActivationService.CodeEmis code = null;
    if (codePour != null) {
      Responsable titulaire = lies.get(codePour);
      if (titulaire.getUtilisateurId() != null) {
        avertissements.add(
            "COMPTE_DEJA_RELIE : ce responsable a déjà un compte ; aucun code n'a été généré.");
      } else {
        code = activation.emettrePourResponsable(titulaire.getId());
      }
    }
    return new Finalisation(
        eleveId, inscription.inscription().getId(), List.copyOf(avertissements), code);
  }

  /** Ce qui manque, étape par étape : le message dit où retourner. */
  private static void exigerComplet(Contenu c) {
    List<String> manques = new ArrayList<>();
    EleveSaisi e = c.eleve();
    if (e == null
        || vide(e.prenoms())
        || vide(e.nom())
        || e.sexe() == null
        || e.dateNaissance() == null
        || vide(e.lieuNaissance())
        || vide(e.nationalite())) {
      manques.add("élève");
    }
    if (c.responsables().stream().noneMatch(ResponsableSaisi::responsableLegal)) {
      manques.add("responsable légal");
    }
    for (ResponsableSaisi r : c.responsables()) {
      boolean identite =
          r.responsableId() != null || !(vide(r.prenoms()) || vide(r.nom()) || vide(r.telephone()));
      if (!identite || r.type() == null) {
        manques.add("responsables");
        break;
      }
    }
    if (c.scolarite() == null || c.scolarite().classeId() == null) {
      manques.add("classe");
    }
    if (!manques.isEmpty()) {
      throw new ErreurMetier(
          "BROUILLON_INCOMPLET",
          TypeErreur.INVALIDE,
          "Brouillon incomplet : " + String.join(", ", manques) + ".");
    }
  }

  private UUID anneeEnCours() {
    return referentiel
        .anneeEnCours()
        .orElseThrow(
            () -> Erreurs.conflit("AUCUNE_ANNEE_EN_COURS", "Aucune année scolaire n'est en cours."))
        .id();
  }

  private UUID anneeOuverte(UUID anneeId) {
    ReferentielScolaire.AnneeScolaireInfo annee = referentiel.annee(anneeId);
    if (annee.cloturee()) {
      throw Erreurs.conflit("ANNEE_CLOTUREE", "Cette année est clôturée.");
    }
    return annee.id();
  }

  private BrouillonInscription charger(UUID id) {
    return brouillons.findById(id).orElseThrow(Erreurs::brouillonIntrouvable);
  }

  /** Avant de clore : un dépôt de pièce en cours se termine d'abord (voir PieceService). */
  private BrouillonInscription verrouiller(UUID id) {
    return brouillons.verrouiller(id).orElseThrow(Erreurs::brouillonIntrouvable);
  }

  private byte[] chiffrer(UUID id, Contenu contenu) {
    String clair;
    try {
      clair = json.writeValueAsString(contenu);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Brouillon non sérialisable.", ex);
    }
    if (clair.getBytes(StandardCharsets.UTF_8).length > TAILLE_MAX) {
      throw Erreurs.invalide("BROUILLON_TROP_GRAND", "Le brouillon dépasse la taille autorisée.");
    }
    return chiffrement.chiffrer(clair, contexte(id));
  }

  private Contenu dechiffrer(BrouillonInscription b) {
    String clair = chiffrement.dechiffrer(b.getContenu(), contexte(b.getId()));
    try {
      return json.readValue(clair, Contenu.class);
    } catch (JacksonException ex) {
      // Ni la cause ni son message : Jackson y cite la valeur saisie (donnée d'un mineur).
      LOG.error(
          "Brouillon {} illisible après déchiffrement ({})",
          b.getId(),
          ex.getClass().getSimpleName());
      throw new ErreurMetier(
          "BROUILLON_ILLISIBLE",
          TypeErreur.CONFLIT,
          "Ce brouillon ne peut plus être relu. Abandonnez-le et recommencez la saisie.");
    }
  }

  /** École et brouillon : un chiffré recopié ailleurs ne se déchiffre pas. */
  static String contexte(UUID brouillonId) {
    return ContexteTenant.exiger() + "|brouillon_inscription|" + brouillonId;
  }

  private static Vue vue(BrouillonInscription b, Contenu contenu) {
    return new Vue(
        b.getId(),
        b.getAnneeId(),
        b.getEtape(),
        contenu,
        b.getAuteurId(),
        b.getModifieLe(),
        b.expireLe(),
        b.getVersion());
  }

  private static boolean vide(String s) {
    return s == null || s.isBlank();
  }

  private static String nonVide(String s) {
    return s == null ? "" : s.strip();
  }
}
