package sn.ecole.etablissement.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.ModeNotation;
import sn.ecole.etablissement.domain.StatutAnnee;
import sn.ecole.etablissement.domain.TypePeriode;
import sn.ecole.shared.application.JournalAudit;

/** Paramètres des cycles : découpage de l'année, mode de notation, base de la moyenne. */
@Service
@Transactional
public class CycleService {

  public record Modification(
      String libelle, TypePeriode typePeriode, ModeNotation modeNotation, int baseMoyenne) {}

  private final CycleRepository cycles;
  private final AnneeScolaireRepository annees;
  private final JournalAudit journal;

  public CycleService(
      CycleRepository cycles, AnneeScolaireRepository annees, JournalAudit journal) {
    this.cycles = cycles;
    this.annees = annees;
    this.journal = journal;
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<Cycle> lister() {
    return cycles.findAll().stream().sorted(Comparator.comparing(Cycle::getCode)).toList();
  }

  /**
   * Réservé au directeur. Une fois une année ouverte, les paramètres ne bougent plus : des notes et
   * des bulletins en dépendront (à durcir quand le module d'évaluation existera).
   */
  @PreAuthorize(Droits.DIRECTION)
  public Cycle modifier(UUID id, int version, Modification m) {
    Cycle cycle =
        cycles
            .findById(id)
            .orElseThrow(() -> Erreurs.introuvable("CYCLE_INTROUVABLE", "Cycle introuvable."));
    Erreurs.verifierVersion(version, cycle);
    boolean changement = !cycle.aLesParametres(m.typePeriode(), m.modeNotation(), m.baseMoyenne());
    if (changement
        && annees.existsByStatutIn(List.of(StatutAnnee.EN_COURS, StatutAnnee.CLOTUREE))) {
      throw Erreurs.conflit(
          "CYCLE_VERROUILLE",
          "Les paramètres d'un cycle ne changent plus une fois une année ouverte.");
    }
    Map<String, Object> avant =
        Map.of(
            "typePeriode", cycle.getTypePeriode().name(),
            "modeNotation", cycle.getModeNotation().name(),
            "baseMoyenne", cycle.getBaseMoyenne());
    cycle.renommer(m.libelle());
    cycle.parametrer(m.typePeriode(), m.modeNotation(), m.baseMoyenne());
    cycles.save(cycle);
    journal.consigner(
        "CYCLE_MODIFIE",
        "Cycle",
        cycle.getId(),
        Map.of(
            "avant",
            avant,
            "apres",
            Map.of(
                "typePeriode", m.typePeriode().name(),
                "modeNotation", m.modeNotation().name(),
                "baseMoyenne", m.baseMoyenne())));
    return cycle;
  }
}
