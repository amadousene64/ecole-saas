package sn.ecole.etablissement.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.ModeNotation;
import sn.ecole.etablissement.TypePeriode;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.NiveauRepository;
import sn.ecole.etablissement.domain.ReglesNotation;
import sn.ecole.shared.application.JournalAudit;

/**
 * Modèle des cycles : découpage de l'année, mode de notation, base de la moyenne. Le directeur le
 * modifie à tout moment ; une année n'en prend une copie qu'à son ouverture (voir {@code
 * AnneeScolaireService#ouvrir}), donc le modèle n'agit que sur les années à venir.
 */
@Service
@Transactional
public class CycleService {

  public record Modification(
      String libelle, TypePeriode typePeriode, ModeNotation modeNotation, int baseMoyenne) {}

  private final CycleRepository cycles;
  private final NiveauRepository niveaux;
  private final JournalAudit journal;

  public CycleService(CycleRepository cycles, NiveauRepository niveaux, JournalAudit journal) {
    this.cycles = cycles;
    this.niveaux = niveaux;
    this.journal = journal;
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<Cycle> lister() {
    return cycles.findAll().stream().sorted(Comparator.comparing(Cycle::getCode)).toList();
  }

  @PreAuthorize(Droits.DIRECTION)
  public Cycle modifier(UUID id, int version, Modification m) {
    Cycle cycle =
        cycles
            .findById(id)
            .orElseThrow(() -> Erreurs.introuvable("CYCLE_INTROUVABLE", "Cycle introuvable."));
    Erreurs.verifierVersion(version, cycle);
    Map<String, Object> avant =
        Map.of(
            "typePeriode", cycle.getTypePeriode().name(),
            "modeNotation", cycle.getModeNotation().name(),
            "baseMoyenne", cycle.getBaseMoyenne());
    cycle.renommer(m.libelle());
    cycle.parametrer(m.typePeriode(), m.modeNotation(), m.baseMoyenne());
    // Un niveau qui surcharge la base à 10 ne peut pas appartenir à un cycle par coefficients.
    niveaux.findAllByOrderByOrdre().stream()
        .filter(n -> n.getCycleId().equals(id) && n.getBaseMoyenne() != null)
        .forEach(n -> ReglesNotation.verifier(m.modeNotation(), n.getBaseMoyenne()));
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
