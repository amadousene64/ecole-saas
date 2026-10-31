package sn.ecole.inscription.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.CodeActivation;
import sn.ecole.inscription.domain.CodeActivation.Cible;
import sn.ecole.inscription.domain.CodeActivationRepository;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.FormatCodeActivation;
import sn.ecole.inscription.domain.FormatCodeActivation.CodeTire;
import sn.ecole.inscription.domain.Responsable;
import sn.ecole.inscription.domain.ResponsableRepository;
import sn.ecole.shared.application.AppartenanceService;
import sn.ecole.shared.application.EmpreinteSecrets;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.LimiteurDebit;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Activation d'un compte par le code à usage unique remis par l'école.
 *
 * <ul>
 *   <li>l'école émet le code (direction, secrétariat) pour un responsable ou un élève ; il n'est
 *       montré qu'une fois et seule son empreinte HMAC est gardée ;
 *   <li>le titulaire, connecté à son compte, le saisit : le compte est relié à la cible et reçoit
 *       le rôle PARENT ou ELEVE dans l'école. Rien d'autre : le lien avec les élèves a été fait par
 *       l'école ;
 *   <li>force brute : 5 essais faux par code (puis épuisé), 10 essais par heure et par compte, 20
 *       par heure et par adresse IP ; tout refus donne la même réponse, pour ne rien révéler sur
 *       l'existence d'un code ; le motif exact va au journal d'audit.
 * </ul>
 */
@Service
@Transactional
public class ActivationService {

  private static final Logger LOG = LoggerFactory.getLogger(ActivationService.class);

  static final int ESSAIS_PAR_COMPTE = 10;
  static final int ESSAIS_PAR_IP = 20;
  static final Duration FENETRE = Duration.ofHours(1);

  /** Code émis : la présentation n'est renvoyée qu'à l'émission, jamais relue ensuite. */
  public record CodeEmis(String code, Instant expireLe) {

    @Override
    public String toString() {
      return "CodeEmis[***]";
    }
  }

  /** Résultat d'une activation réussie. */
  public record Activation(RoleEcole role, UUID cibleId) {}

  /**
   * Refus d'activation : la transaction est validée malgré l'exception, pour que l'essai faux reste
   * compté sur le code.
   */
  static final class ActivationRefusee extends ErreurMetier {
    ActivationRefusee() {
      super(
          "ACTIVATION_IMPOSSIBLE",
          TypeErreur.INVALIDE,
          "Code invalide ou expiré. Demandez un nouveau code à l'école.");
    }
  }

  private final CodeActivationRepository codes;
  private final ResponsableRepository responsables;
  private final EleveService eleves;
  private final EmpreinteSecrets empreintes;
  private final LimiteurDebit limiteur;
  private final AppartenanceService appartenances;
  private final JournalAudit journal;
  private final ActivationEleve activationEleve;

  public ActivationService(
      CodeActivationRepository codes,
      ResponsableRepository responsables,
      EleveService eleves,
      EmpreinteSecrets empreintes,
      LimiteurDebit limiteur,
      AppartenanceService appartenances,
      JournalAudit journal,
      ActivationEleve activationEleve) {
    this.codes = codes;
    this.responsables = responsables;
    this.eleves = eleves;
    this.empreintes = empreintes;
    this.limiteur = limiteur;
    this.appartenances = appartenances;
    this.journal = journal;
    this.activationEleve = activationEleve;
  }

  /** Émet un code pour un responsable ; les codes encore actifs pour lui sont révoqués. */
  @PreAuthorize(Droits.ECRITURE)
  public CodeEmis emettrePourResponsable(UUID responsableId) {
    Responsable r =
        responsables
            .findById(responsableId)
            .orElseThrow(
                () -> Erreurs.introuvable("RESPONSABLE_INTROUVABLE", "Responsable introuvable."));
    if (r.getUtilisateurId() != null) {
      throw Erreurs.conflit(
          "COMPTE_DEJA_RELIE",
          "Un compte est déjà relié à ce responsable : le directeur doit d'abord le détacher.");
    }
    return emettre(Cible.RESPONSABLE, responsableId);
  }

  /** Émet un code pour un élève du collège ou du lycée ({@link ActivationEleve}). */
  @PreAuthorize(Droits.ECRITURE)
  public CodeEmis emettrePourEleve(UUID eleveId) {
    Eleve eleve = eleves.charger(eleveId);
    if (eleve.getUtilisateurId() != null) {
      throw Erreurs.conflit("COMPTE_DEJA_RELIE", "Un compte est déjà relié à cet élève.");
    }
    activationEleve.verifierEligible(eleveId);
    return emettre(Cible.ELEVE, eleveId);
  }

  /**
   * Activation par l'utilisateur connecté, qui n'est pas encore membre de l'école (chaîne de
   * sécurité dédiée : aucun rôle).
   *
   * @param adresseIp adresse du client, pour la limitation de débit (jamais stockée en clair)
   */
  @PreAuthorize("isAuthenticated()")
  @Transactional(noRollbackFor = ActivationRefusee.class)
  public Activation activer(String saisie, String adresseIp) {
    UUID moi = Erreurs.utilisateurCourant();
    UUID ecole = ContexteTenant.exiger();
    boolean ipOk = limiteur.autoriser("activation:ip:" + hacher(adresseIp), ESSAIS_PAR_IP, FENETRE);
    boolean compteOk = limiteur.autoriser("activation:compte:" + moi, ESSAIS_PAR_COMPTE, FENETRE);
    if (!ipOk || !compteOk) {
      LOG.warn("Activation : limite de tentatives atteinte (école {})", ecole);
      throw new ErreurMetier(
          "TROP_DE_TENTATIVES",
          TypeErreur.TROP_DE_TENTATIVES,
          "Trop de tentatives. Réessayez dans une heure.");
    }
    Optional<CodeTire> lu = FormatCodeActivation.lire(saisie);
    if (lu.isEmpty()) {
      throw refus(moi, "FORMAT", null);
    }
    Optional<CodeActivation> trouve = codes.verrouillerActif(lu.get().identifiant());
    if (trouve.isEmpty()) {
      throw refus(moi, "INCONNU", null);
    }
    CodeActivation code = trouve.get();
    Instant maintenant = Instant.now();
    if (!empreintes.correspond(
        lu.get().secret(), contexte(ecole, code.getIdentifiant()), code.getEmpreinte())) {
      code.enregistrerEchec();
      codes.save(code);
      throw refus(
          moi,
          code.getStatut() == CodeActivation.Statut.EPUISE ? "SECRET_FAUX_EPUISE" : "SECRET_FAUX",
          code.getId());
    }
    if (!code.utilisable(maintenant)) {
      throw refus(moi, "EXPIRE", code.getId());
    }
    RoleEcole role =
        switch (code.getCible()) {
          case RESPONSABLE -> relierResponsable(code.getCibleId(), moi);
          case ELEVE -> relierEleve(code.getCibleId(), moi);
        };
    code.utiliser(moi, maintenant);
    codes.save(code);
    appartenances.rattacherParActivation(role);
    journal.consigner(
        "COMPTE_ACTIVE",
        code.getCible() == Cible.RESPONSABLE ? "Responsable" : "Eleve",
        code.getCibleId(),
        Map.of("code", code.getId(), "role", role));
    return new Activation(role, code.getCibleId());
  }

  private RoleEcole relierResponsable(UUID responsableId, UUID moi) {
    Responsable r =
        responsables.findById(responsableId).orElseThrow(() -> refus(moi, "CIBLE_ABSENTE", null));
    responsables
        .findByUtilisateurId(moi)
        .filter(autre -> !autre.getId().equals(responsableId))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "COMPTE_DEJA_UTILISE",
                  "Votre compte est déjà relié à un autre responsable de cette école.");
            });
    r.relierCompte(moi);
    responsables.save(r);
    return RoleEcole.PARENT;
  }

  private RoleEcole relierEleve(UUID eleveId, UUID moi) {
    activationEleve.relier(eleveId, moi);
    return RoleEcole.ELEVE;
  }

  private CodeEmis emettre(Cible cible, UUID cibleId) {
    for (CodeActivation ancien :
        codes.findByCibleAndCibleIdAndStatut(cible, cibleId, CodeActivation.Statut.ACTIF)) {
      ancien.revoquer();
      codes.save(ancien);
    }
    UUID ecole = ContexteTenant.exiger();
    CodeTire tire = FormatCodeActivation.tirer();
    // L'identifiant doit être unique parmi les codes actifs de l'école.
    for (int essai = 0;
        codes.existsByIdentifiantAndStatut(tire.identifiant(), CodeActivation.Statut.ACTIF);
        essai++) {
      if (essai > 20) {
        throw new IllegalStateException("Aucun identifiant de code libre.");
      }
      tire = FormatCodeActivation.tirer();
    }
    CodeActivation code =
        codes.save(
            CodeActivation.emettre(
                cible,
                cibleId,
                tire.identifiant(),
                empreintes.empreinte(tire.secret(), contexte(ecole, tire.identifiant())),
                Instant.now()));
    journal.consigner(
        "CODE_ACTIVATION_EMIS",
        cible == Cible.RESPONSABLE ? "Responsable" : "Eleve",
        cibleId,
        Map.of("code", code.getId(), "expireLe", code.getExpireLe().toString()));
    return new CodeEmis(tire.presentation(), code.getExpireLe());
  }

  /** Motif détaillé au journal d'audit seulement ; la réponse est la même pour tous. */
  private ActivationRefusee refus(UUID moi, String motif, UUID codeId) {
    journal.consigner(
        "ACTIVATION_REFUSEE",
        "Utilisateur",
        moi,
        codeId == null ? Map.of("motif", motif) : Map.of("motif", motif, "code", codeId));
    return new ActivationRefusee();
  }

  /** Le secret est lié à son école et à son identifiant. */
  static String contexte(UUID ecole, String identifiant) {
    return ecole + "|code|" + identifiant;
  }

  /** Clé de compteur pour une adresse IP : HMAC (clé hors base), jamais l'adresse en clair. */
  private String hacher(String adresseIp) {
    return HexFormat.of()
        .formatHex(empreintes.empreinte(String.valueOf(adresseIp), "limite|ip"), 0, 16);
  }
}
