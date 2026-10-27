package sn.ecole.etablissement.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.Periode;
import sn.ecole.etablissement.domain.PeriodePrevue;
import sn.ecole.etablissement.domain.PeriodeRepository;
import sn.ecole.etablissement.domain.ReglesPeriodes;
import sn.ecole.etablissement.domain.StatutAnnee;
import sn.ecole.shared.application.JournalAudit;

/** Périodes (trimestres ou semestres) d'une année : consultation, génération et ajustement. */
@Service
@Transactional
public class PeriodeService {

  private final AnneeScolaireRepository annees;
  private final CycleRepository cycles;
  private final PeriodeRepository periodes;
  private final FabriquePeriodes fabrique;
  private final JournalAudit journal;

  public PeriodeService(
      AnneeScolaireRepository annees,
      CycleRepository cycles,
      PeriodeRepository periodes,
      FabriquePeriodes fabrique,
      JournalAudit journal) {
    this.annees = annees;
    this.cycles = cycles;
    this.periodes = periodes;
    this.fabrique = fabrique;
    this.journal = journal;
  }

  /** Par cycle (ordre des cycles) puis par numéro. */
  @PreAuthorize(Droits.LECTURE_TOUS)
  @Transactional(readOnly = true)
  public List<Periode> lister(UUID anneeId) {
    charger(anneeId);
    return trier(periodes.findByAnneeId(anneeId));
  }

  /**
   * Remplace les périodes par celles du modèle de chaque cycle ; année en préparation seulement.
   */
  @PreAuthorize(Droits.ECRITURE)
  public List<Periode> generer(UUID anneeId) {
    AnneeScolaire annee = charger(anneeId);
    if (annee.getStatut() != StatutAnnee.PREPARATION) {
      throw Erreurs.conflit(
          "ANNEE_NON_EN_PREPARATION",
          "Les périodes ne se régénèrent que pour une année en préparation.");
    }
    fabrique.regenerer(annee);
    journal.consigner("PERIODES_GENEREES", "AnneeScolaire", anneeId, Map.of());
    return trier(periodes.findByAnneeId(anneeId));
  }

  @PreAuthorize(Droits.ECRITURE)
  public Periode modifier(
      UUID anneeId, UUID periodeId, int version, String libelle, LocalDate debut, LocalDate fin) {
    AnneeScolaire annee = charger(anneeId);
    annee.verifierModifiable();
    Periode periode =
        periodes
            .findById(periodeId)
            .filter(p -> p.getAnneeId().equals(anneeId))
            .orElseThrow(() -> Erreurs.introuvable("PERIODE_INTROUVABLE", "Période introuvable."));
    Erreurs.verifierVersion(version, periode);
    List<PeriodePrevue> candidates = new ArrayList<>();
    for (Periode autre : periodes.findByAnneeIdAndCycleId(anneeId, periode.getCycleId())) {
      candidates.add(
          autre.getId().equals(periodeId)
              ? new PeriodePrevue(autre.getNumero(), debut, fin)
              : autre.enPrevue());
    }
    ReglesPeriodes.verifier(annee, candidates);
    periode.modifier(libelle, debut, fin);
    return periodes.save(periode);
  }

  private List<Periode> trier(List<Periode> liste) {
    Map<UUID, Integer> rangDuCycle = new HashMap<>();
    cycles.findAll().forEach(c -> rangDuCycle.put(c.getId(), c.getCode().ordinal()));
    return liste.stream()
        .sorted(
            Comparator.comparingInt((Periode p) -> rangDuCycle.getOrDefault(p.getCycleId(), 99))
                .thenComparingInt(Periode::getNumero))
        .toList();
  }

  private AnneeScolaire charger(UUID id) {
    return annees
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("ANNEE_INTROUVABLE", "Année scolaire introuvable."));
  }
}
