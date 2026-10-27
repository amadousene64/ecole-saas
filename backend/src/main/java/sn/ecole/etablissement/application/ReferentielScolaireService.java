package sn.ecole.etablissement.application;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.Periode;
import sn.ecole.etablissement.domain.PeriodeRepository;
import sn.ecole.etablissement.domain.StatutAnnee;

/** Lecture de la structure scolaire pour les autres modules (année en cours, périodes). */
@Service
@Transactional(readOnly = true)
public class ReferentielScolaireService implements ReferentielScolaire {

  private final AnneeScolaireRepository annees;
  private final PeriodeRepository periodes;

  public ReferentielScolaireService(AnneeScolaireRepository annees, PeriodeRepository periodes) {
    this.annees = annees;
    this.periodes = periodes;
  }

  @Override
  @PreAuthorize(Droits.LECTURE_TOUS)
  public Optional<AnneeEnCours> anneeEnCours() {
    return annees
        .findByStatut(StatutAnnee.EN_COURS)
        .map(a -> new AnneeEnCours(a.getId(), a.getLibelle(), a.getDateDebut(), a.getDateFin()));
  }

  @Override
  @PreAuthorize(Droits.LECTURE_TOUS)
  public List<PeriodeScolaire> periodes(UUID anneeId) {
    return periodes.findByAnneeId(anneeId).stream()
        .sorted(Comparator.comparing(Periode::getCycleId).thenComparingInt(Periode::getNumero))
        .map(
            p ->
                new PeriodeScolaire(
                    p.getId(),
                    p.getCycleId(),
                    p.getNumero(),
                    p.getLibelle(),
                    p.getDateDebut(),
                    p.getDateFin()))
        .toList();
  }
}
