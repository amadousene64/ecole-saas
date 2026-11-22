package sn.ecole.personnel.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.ReferentielScolaire.AnneeScolaireInfo;
import sn.ecole.etablissement.ReferentielScolaire.ClasseScolaire;
import sn.ecole.etablissement.ReferentielScolaire.MatiereScolaire;
import sn.ecole.personnel.domain.Affectation;
import sn.ecole.personnel.domain.AffectationRepository;
import sn.ecole.personnel.domain.TypeAffectation;
import sn.ecole.shared.application.AppartenanceService;
import sn.ecole.shared.application.InvalidationDroits;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Affectations du personnel par année scolaire. Une seule personne à la fois pour une matière dans
 * une classe, un seul maître titulaire par classe : un remplacement termine l'affectation en cours
 * à la date où commence la nouvelle. Chaque création, modification ou fin est journalisée et
 * invalide aussitôt le périmètre mis en cache de la personne concernée.
 */
@Service
@Transactional
public class AffectationService {

  /** Une affectation, avec les libellés à afficher. */
  public record Vue(Affectation affectation, String classe, String matiere, boolean enCours) {}

  /** Saisie d'une affectation ; {@code cycles} pour la surveillance (un ou plusieurs). */
  public record Saisie(
      TypeAffectation type,
      UUID anneeId,
      UUID utilisateurId,
      UUID classeId,
      UUID matiereId,
      Set<CodeCycle> cycles,
      LocalDate debut,
      LocalDate fin) {}

  private final AffectationRepository affectations;
  private final ReferentielScolaire referentiel;
  private final AppartenanceService appartenances;
  private final InvalidationDroits invalidation;
  private final JournalAudit journal;
  private final Clock horloge;

  public AffectationService(
      AffectationRepository affectations,
      ReferentielScolaire referentiel,
      AppartenanceService appartenances,
      InvalidationDroits invalidation,
      JournalAudit journal,
      Clock horloge) {
    this.affectations = affectations;
    this.referentiel = referentiel;
    this.appartenances = appartenances;
    this.invalidation = invalidation;
    this.journal = journal;
    this.horloge = horloge;
  }

  /** Affectations de l'année, en cours et terminées (historique), par date de début. */
  @PreAuthorize(Droits.GESTION)
  @Transactional(readOnly = true)
  public List<Vue> lister(UUID anneeId) {
    referentiel.annee(anneeId);
    return vues(affectations.findByAnneeIdOrderByDebutAsc(anneeId));
  }

  /** Les affectations en cours de l'utilisateur connecté (« Mes classes »). */
  @PreAuthorize(Droits.MES_AFFECTATIONS)
  @Transactional(readOnly = true)
  public List<Vue> miennes() {
    LocalDate aujourdhui = aujourdhui();
    UUID moi = Erreurs.utilisateurCourant();
    return vues(
        affectations.findByUtilisateurId(moi).stream()
            .filter(a -> a.estEnCoursLe(aujourdhui))
            .toList());
  }

  /**
   * Crée l'affectation (une par cycle pour une surveillance sur plusieurs cycles).
   *
   * @throws ErreurMetier si la personne n'a pas le rôle voulu, si la classe ou la matière ne
   *     conviennent pas, ou si la place est déjà prise sur la période
   */
  @PreAuthorize(Droits.GESTION)
  public List<Affectation> creer(Saisie s) {
    Objects.requireNonNull(s.type(), "type");
    AnneeScolaireInfo annee = referentiel.annee(s.anneeId());
    if (annee.cloturee()) {
      throw Erreurs.conflit("ANNEE_CLOTUREE", "L'année " + annee.libelle() + " est clôturée.");
    }
    verifierDansLAnnee(annee, s.debut(), s.fin());
    List<Affectation> creees = new ArrayList<>();
    switch (s.type()) {
      case ENSEIGNEMENT -> {
        exigerRole(s.utilisateurId(), RoleEcole.ENSEIGNANT);
        ClasseScolaire classe = classeDeLAnnee(s.classeId(), annee);
        MatiereScolaire matiere = matiere(s.matiereId());
        if (!matiere.cycles().contains(classe.codeCycle())) {
          throw Erreurs.invalide(
              "MATIERE_HORS_CYCLE",
              matiere.libelle()
                  + " n'est pas enseignée dans le cycle de la classe "
                  + classe.nom()
                  + ".");
        }
        creees.add(
            Affectation.enseignement(
                s.anneeId(), s.utilisateurId(), classe.id(), matiere.id(), s.debut(), s.fin()));
      }
      case TITULAIRE -> {
        exigerRole(s.utilisateurId(), RoleEcole.ENSEIGNANT);
        ClasseScolaire classe = classeDeLAnnee(s.classeId(), annee);
        if (classe.codeCycle() != CodeCycle.ELEMENTAIRE) {
          throw Erreurs.invalide(
              "TITULAIRE_ELEMENTAIRE",
              "Un maître titulaire s'affecte à une classe de l'élémentaire ; au moyen et au"
                  + " secondaire, affectez une matière.");
        }
        creees.add(
            Affectation.titulaire(s.anneeId(), s.utilisateurId(), classe.id(), s.debut(), s.fin()));
      }
      case SURVEILLANCE -> {
        exigerRole(s.utilisateurId(), RoleEcole.SURVEILLANT);
        if (s.cycles() == null || s.cycles().isEmpty()) {
          throw Erreurs.invalide("CYCLES_REQUIS", "Choisissez au moins un cycle à surveiller.");
        }
        for (CodeCycle cycle : Set.copyOf(s.cycles())) {
          creees.add(
              Affectation.surveillance(s.anneeId(), s.utilisateurId(), cycle, s.debut(), s.fin()));
        }
      }
    }
    List<Affectation> enregistrees = new ArrayList<>();
    for (Affectation a : creees) {
      verifierPlaceLibre(a, a.getDebut(), a.getFin());
      Affectation enregistree = affectations.save(a);
      journal.consigner(
          "AFFECTATION_CREEE", "Affectation", enregistree.getId(), resume(enregistree));
      enregistrees.add(enregistree);
    }
    invalidation.auCommit(s.utilisateurId());
    return enregistrees;
  }

  /** Corrige la période (dates mal saisies, remplacement prévu à l'avance). */
  @PreAuthorize(Droits.GESTION)
  public Affectation changerPeriode(UUID id, int version, LocalDate debut, LocalDate fin) {
    Affectation a = charger(id);
    Erreurs.verifierVersion(version, a);
    AnneeScolaireInfo annee = referentiel.annee(a.getAnneeId());
    verifierDansLAnnee(annee, debut, fin);
    Map<String, Object> avant = resume(a);
    verifierPlaceLibre(a, debut, fin);
    a.changerPeriode(debut, fin);
    Affectation modifiee = affectations.save(a);
    journal.consigner(
        "AFFECTATION_MODIFIEE",
        "Affectation",
        id,
        Map.of("avant", avant, "apres", resume(modifiee)));
    invalidation.auCommit(a.getUtilisateurId());
    return modifiee;
  }

  /**
   * Termine l'affectation à {@code fin} (aujourd'hui par défaut : effet immédiat). Une affectation
   * qui n'a pas encore commencé à cette date est annulée : elle n'a jamais valu.
   */
  @PreAuthorize(Droits.GESTION)
  public void terminer(UUID id, int version, LocalDate fin) {
    Affectation a = charger(id);
    Erreurs.verifierVersion(version, a);
    LocalDate date = fin != null ? fin : aujourdhui();
    if (!date.isAfter(a.getDebut())) {
      a.marquerAnnulee(Instant.now(horloge));
      affectations.save(a);
      journal.consigner("AFFECTATION_ANNULEE", "Affectation", id, resume(a));
    } else {
      a.terminer(date);
      affectations.save(a);
      journal.consigner("AFFECTATION_TERMINEE", "Affectation", id, resume(a));
    }
    invalidation.auCommit(a.getUtilisateurId());
  }

  private void verifierDansLAnnee(AnneeScolaireInfo annee, LocalDate debut, LocalDate fin) {
    if (debut == null) {
      throw Erreurs.invalide("PERIODE_INVALIDE", "La date de début est obligatoire.");
    }
    if (debut.isBefore(annee.dateDebut()) || debut.isAfter(annee.dateFin())) {
      throw Erreurs.invalide(
          "PERIODE_INVALIDE",
          "L'affectation commence pendant l'année "
              + annee.libelle()
              + " (du "
              + annee.dateDebut()
              + " au "
              + annee.dateFin()
              + ").");
    }
    if (fin != null && !fin.isAfter(debut)) {
      throw Erreurs.invalide("PERIODE_INVALIDE", "La fin doit suivre le début de l'affectation.");
    }
  }

  /** Une matière dans une classe, un titulaire par classe, un cycle par surveillant, à la fois. */
  private void verifierPlaceLibre(Affectation nouvelle, LocalDate debut, LocalDate fin) {
    Collection<Affectation> candidates =
        switch (nouvelle.getType()) {
          case ENSEIGNEMENT, TITULAIRE -> affectations.findByClasseId(nouvelle.getClasseId());
          case SURVEILLANCE -> affectations.findByUtilisateurId(nouvelle.getUtilisateurId());
        };
    for (Affectation autre : candidates) {
      if (autre.getId().equals(nouvelle.getId())
          || autre.getType() != nouvelle.getType()
          || !autre.chevauche(debut, fin)) {
        continue;
      }
      boolean memePlace =
          switch (nouvelle.getType()) {
            case ENSEIGNEMENT -> autre.getMatiereId().equals(nouvelle.getMatiereId());
            case TITULAIRE -> true;
            case SURVEILLANCE -> autre.getCodeCycle() == nouvelle.getCodeCycle();
          };
      if (memePlace) {
        throw Erreurs.conflit(
            "AFFECTATION_EN_CONFLIT",
            switch (nouvelle.getType()) {
              case ENSEIGNEMENT ->
                  "Cette matière est déjà confiée à quelqu'un dans cette classe sur la période :"
                      + " terminez son affectation à la date du remplacement.";
              case TITULAIRE ->
                  "Cette classe a déjà un maître titulaire sur la période : terminez son"
                      + " affectation à la date du remplacement.";
              case SURVEILLANCE -> "Ce surveillant est déjà affecté à ce cycle sur la période.";
            });
      }
    }
  }

  private void exigerRole(UUID utilisateurId, RoleEcole role) {
    if (utilisateurId == null
        || !appartenances.rolesPour(ContexteTenant.exiger(), utilisateurId).contains(role)) {
      throw Erreurs.invalide(
          "ROLE_REQUIS",
          role == RoleEcole.ENSEIGNANT
              ? "Cette personne n'est pas enseignante dans l'école."
              : "Cette personne n'est pas surveillante dans l'école.");
    }
  }

  private ClasseScolaire classeDeLAnnee(UUID classeId, AnneeScolaireInfo annee) {
    if (classeId == null) {
      throw Erreurs.invalide("CLASSE_REQUISE", "Choisissez une classe.");
    }
    ClasseScolaire classe = referentiel.classe(classeId);
    if (!classe.anneeId().equals(annee.id())) {
      throw Erreurs.invalide(
          "CLASSE_AUTRE_ANNEE", "La classe " + classe.nom() + " n'est pas de l'année choisie.");
    }
    return classe;
  }

  private MatiereScolaire matiere(UUID matiereId) {
    return referentiel.matieres().stream()
        .filter(m -> m.id().equals(matiereId) && m.actif())
        .findFirst()
        .orElseThrow(
            () -> Erreurs.introuvable("MATIERE_INTROUVABLE", "Matière introuvable ou inactive."));
  }

  private List<Vue> vues(List<Affectation> liste) {
    if (liste.isEmpty()) {
      return List.of();
    }
    LocalDate aujourdhui = aujourdhui();
    Map<UUID, String> classes =
        referentiel.nomsClasses(
            liste.stream().map(Affectation::getClasseId).filter(Objects::nonNull).toList());
    Map<UUID, String> matieres = new HashMap<>();
    referentiel.matieres().forEach(m -> matieres.put(m.id(), m.libelle()));
    return liste.stream()
        .map(
            a ->
                new Vue(
                    a,
                    a.getClasseId() == null ? null : classes.get(a.getClasseId()),
                    a.getMatiereId() == null ? null : matieres.get(a.getMatiereId()),
                    a.estEnCoursLe(aujourdhui)))
        .toList();
  }

  private Affectation charger(UUID id) {
    return affectations
        .findById(id)
        .orElseThrow(
            () -> Erreurs.introuvable("AFFECTATION_INTROUVABLE", "Affectation introuvable."));
  }

  private LocalDate aujourdhui() {
    return LocalDate.now(horloge);
  }

  /** Détails du journal : identifiants et dates, jamais de nom. */
  private static Map<String, Object> resume(Affectation a) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("type", a.getType());
    d.put("utilisateur", a.getUtilisateurId());
    d.put("annee", a.getAnneeId());
    if (a.getClasseId() != null) {
      d.put("classe", a.getClasseId());
    }
    if (a.getMatiereId() != null) {
      d.put("matiere", a.getMatiereId());
    }
    if (a.getCodeCycle() != null) {
      d.put("cycle", a.getCodeCycle());
    }
    d.put("debut", a.getDebut().toString());
    d.put("fin", a.getFin() == null ? "" : a.getFin().toString());
    return d;
  }
}
