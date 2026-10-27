package sn.ecole.etablissement.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.AnneeScolaireCloturee;
import sn.ecole.etablissement.AnneeScolaireOuverte;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.ClasseRepository;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.EvenementCalendrier;
import sn.ecole.etablissement.domain.EvenementCalendrierRepository;
import sn.ecole.etablissement.domain.JoursFeries;
import sn.ecole.etablissement.domain.Periode;
import sn.ecole.etablissement.domain.PeriodeRepository;
import sn.ecole.etablissement.domain.ReglesPeriodes;
import sn.ecole.etablissement.domain.StatutAnnee;
import sn.ecole.etablissement.domain.TypeEvenement;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ContexteTenant;

/**
 * Années scolaires : création (avec périodes et jours fériés), ouverture, clôture. Une année
 * clôturée est en lecture seule ; une seule année est EN_COURS par école.
 */
@Service
@Transactional
public class AnneeScolaireService {

  private final AnneeScolaireRepository annees;
  private final CycleRepository cycles;
  private final PeriodeRepository periodes;
  private final EvenementCalendrierRepository calendrier;
  private final ClasseRepository classes;
  private final FabriquePeriodes fabrique;
  private final JournalAudit journal;
  private final ApplicationEventPublisher evenements;

  public AnneeScolaireService(
      AnneeScolaireRepository annees,
      CycleRepository cycles,
      PeriodeRepository periodes,
      EvenementCalendrierRepository calendrier,
      ClasseRepository classes,
      FabriquePeriodes fabrique,
      JournalAudit journal,
      ApplicationEventPublisher evenements) {
    this.annees = annees;
    this.cycles = cycles;
    this.periodes = periodes;
    this.calendrier = calendrier;
    this.classes = classes;
    this.fabrique = fabrique;
    this.journal = journal;
    this.evenements = evenements;
  }

  @PreAuthorize(Droits.LECTURE_TOUS)
  @Transactional(readOnly = true)
  public List<AnneeScolaire> lister() {
    return annees.findAllByOrderByDateDebutDesc();
  }

  @PreAuthorize(Droits.LECTURE_TOUS)
  @Transactional(readOnly = true)
  public AnneeScolaire lire(UUID id) {
    return charger(id);
  }

  @PreAuthorize(Droits.LECTURE_TOUS)
  @Transactional(readOnly = true)
  public Optional<AnneeScolaire> enCours() {
    return annees.findByStatut(StatutAnnee.EN_COURS);
  }

  /** Crée l'année en PREPARATION avec ses périodes par cycle et ses jours fériés (modifiables). */
  @PreAuthorize(Droits.ECRITURE)
  public AnneeScolaire creer(String libelle, LocalDate dateDebut, LocalDate dateFin) {
    AnneeScolaire annee = AnneeScolaire.creer(libelle, dateDebut, dateFin);
    verifierLibelleLibre(annee.getLibelle(), null);
    annees.save(annee);
    fabrique.regenerer(annee);
    for (JoursFeries.Ferie ferie : JoursFeries.pour(dateDebut, dateFin)) {
      calendrier.save(
          EvenementCalendrier.creer(
              annee.getId(), TypeEvenement.FERIE, ferie.libelle(), ferie.date(), ferie.date()));
    }
    journal.consigner("ANNEE_CREEE", "AnneeScolaire", annee.getId(), Map.of("libelle", libelle));
    return annee;
  }

  /** Les nouvelles dates doivent encore contenir les périodes et le calendrier de l'année. */
  @PreAuthorize(Droits.ECRITURE)
  public AnneeScolaire modifier(
      UUID id, int version, String libelle, LocalDate dateDebut, LocalDate dateFin) {
    AnneeScolaire annee = charger(id);
    Erreurs.verifierVersion(version, annee);
    annee.modifier(libelle, dateDebut, dateFin);
    verifierLibelleLibre(annee.getLibelle(), id);
    for (Cycle cycle : cycles.findAll()) {
      ReglesPeriodes.verifier(
          annee,
          periodes.findByAnneeIdAndCycleId(id, cycle.getId()).stream()
              .map(Periode::enPrevue)
              .toList());
    }
    for (EvenementCalendrier evenement : calendrier.findByAnneeIdOrderByDateDebut(id)) {
      if (!annee.contient(evenement.getDateDebut()) || !annee.contient(evenement.getDateFin())) {
        throw Erreurs.regle(
            "EVENEMENT_HORS_ANNEE", "Le calendrier de l'année dépasse les nouvelles dates.");
      }
    }
    return annees.save(annee);
  }

  /** Une année en préparation et vide : suppression logique avec ses périodes et son calendrier. */
  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID id) {
    AnneeScolaire annee = charger(id);
    if (!classes.findByAnneeIdOrderByNom(id).isEmpty()) {
      throw Erreurs.conflit("ANNEE_NON_VIDE", "Des classes sont rattachées à cette année.");
    }
    Instant maintenant = Instant.now();
    annee.supprimer(maintenant);
    for (Periode periode : periodes.findByAnneeId(id)) {
      periode.supprimer(maintenant);
      periodes.save(periode);
    }
    for (EvenementCalendrier evenement : calendrier.findByAnneeIdOrderByDateDebut(id)) {
      evenement.supprimer(maintenant);
      calendrier.save(evenement);
    }
    annees.save(annee);
    journal.consigner(
        "ANNEE_SUPPRIMEE", "AnneeScolaire", id, Map.of("libelle", annee.getLibelle()));
  }

  /** Réservé au directeur. Exige des périodes complètes et aucune autre année en cours. */
  @PreAuthorize(Droits.DIRECTION)
  public AnneeScolaire ouvrir(UUID id) {
    AnneeScolaire annee = charger(id);
    if (annee.getStatut() == StatutAnnee.PREPARATION) {
      annees
          .findByStatut(StatutAnnee.EN_COURS)
          .ifPresent(
              autre -> {
                throw anneeDejaEnCours(autre);
              });
      verifierPeriodesCompletes(id);
    }
    annee.ouvrir();
    try {
      annees.saveAndFlush(annee);
    } catch (DataIntegrityViolationException e) {
      // Deux ouvertures simultanées : l'index unique partiel départage.
      throw Erreurs.conflit("ANNEE_DEJA_EN_COURS", "Une autre année est déjà en cours.");
    }
    journal.consigner("ANNEE_OUVERTE", "AnneeScolaire", id, Map.of("libelle", annee.getLibelle()));
    evenements.publishEvent(
        new AnneeScolaireOuverte(
            ContexteTenant.exiger(),
            id,
            annee.getLibelle(),
            annee.getDateDebut(),
            annee.getDateFin()));
    return annee;
  }

  /**
   * Réservé au directeur. Définitif : l'année et tout ce qui lui est rattaché passent en lecture
   * seule.
   */
  @PreAuthorize(Droits.DIRECTION)
  public AnneeScolaire cloturer(UUID id) {
    AnneeScolaire annee = charger(id);
    annee.cloturer();
    annees.save(annee);
    journal.consigner("ANNEE_CLOTUREE", "AnneeScolaire", id, Map.of("libelle", annee.getLibelle()));
    evenements.publishEvent(
        new AnneeScolaireCloturee(
            ContexteTenant.exiger(),
            id,
            annee.getLibelle(),
            annee.getDateDebut(),
            annee.getDateFin()));
    return annee;
  }

  private void verifierPeriodesCompletes(UUID anneeId) {
    for (Cycle cycle : cycles.findAll()) {
      List<Integer> numeros =
          periodes.findByAnneeIdAndCycleId(anneeId, cycle.getId()).stream()
              .map(Periode::getNumero)
              .sorted()
              .toList();
      List<Integer> attendus =
          java.util.stream.IntStream.rangeClosed(1, cycle.getTypePeriode().nombre())
              .boxed()
              .toList();
      if (!numeros.equals(attendus)) {
        throw Erreurs.regle(
            "PERIODES_INCOMPLETES",
            "Le cycle « "
                + cycle.getLibelle()
                + " » doit avoir "
                + cycle.getTypePeriode().nombre()
                + " "
                + cycle.getTypePeriode().name().toLowerCase()
                + "s avant l'ouverture.");
      }
    }
  }

  private static RuntimeException anneeDejaEnCours(AnneeScolaire autre) {
    return Erreurs.conflit(
        "ANNEE_DEJA_EN_COURS", "L'année " + autre.getLibelle() + " est déjà en cours.");
  }

  private void verifierLibelleLibre(String libelle, UUID sauf) {
    annees
        .findByLibelle(libelle)
        .filter(autre -> !autre.getId().equals(sauf))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit("ANNEE_EXISTANTE", "L'année " + libelle + " existe déjà.");
            });
  }

  private AnneeScolaire charger(UUID id) {
    return annees
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("ANNEE_INTROUVABLE", "Année scolaire introuvable."));
  }
}
