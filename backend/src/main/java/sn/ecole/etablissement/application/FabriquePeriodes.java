package sn.ecole.etablissement.application;

import java.time.Instant;
import org.springframework.stereotype.Component;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.Periode;
import sn.ecole.etablissement.domain.PeriodePrevue;
import sn.ecole.etablissement.domain.PeriodeRepository;
import sn.ecole.etablissement.domain.ReglesPeriodes;

/** Génère les périodes d'une année pour chaque cycle, selon son type de période. */
@Component
class FabriquePeriodes {

  private final CycleRepository cycles;
  private final PeriodeRepository periodes;

  FabriquePeriodes(CycleRepository cycles, PeriodeRepository periodes) {
    this.cycles = cycles;
    this.periodes = periodes;
  }

  /** Remplace les périodes existantes de l'année par celles générées. */
  void regenerer(AnneeScolaire annee) {
    Instant maintenant = Instant.now();
    for (Periode ancienne : periodes.findByAnneeId(annee.getId())) {
      ancienne.supprimer(maintenant);
      periodes.saveAndFlush(ancienne);
    }
    for (Cycle cycle : cycles.findAll()) {
      for (PeriodePrevue prevue : ReglesPeriodes.generer(annee, cycle.getTypePeriode())) {
        periodes.save(
            Periode.creer(
                annee.getId(),
                cycle.getId(),
                prevue,
                cycle.getTypePeriode().libelle(prevue.numero())));
      }
    }
  }
}
