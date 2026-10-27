package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import sn.ecole.etablissement.ModeNotation;
import sn.ecole.etablissement.TypePeriode;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Copie des paramètres d'un cycle prise à l'ouverture d'une année. Jamais modifiée ensuite : les
 * bulletins d'une année se calculent avec les paramètres qu'elle avait, pas ceux d'aujourd'hui.
 */
@Entity
@Immutable
@Table(name = "parametres_cycle_annee")
public class ParametresCycleAnnee extends TenantAwareEntity {

  @Column(name = "annee_id", nullable = false, updatable = false)
  private UUID anneeId;

  @Column(name = "cycle_id", nullable = false, updatable = false)
  private UUID cycleId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type_periode", nullable = false, updatable = false, length = 10)
  private TypePeriode typePeriode;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode_notation", nullable = false, updatable = false, length = 12)
  private ModeNotation modeNotation;

  @Column(name = "base_moyenne", nullable = false, updatable = false)
  private int baseMoyenne;

  protected ParametresCycleAnnee() {}

  private ParametresCycleAnnee(UUID anneeId, Cycle cycle) {
    this.anneeId = Objects.requireNonNull(anneeId, "anneeId");
    this.cycleId = cycle.getId();
    this.typePeriode = cycle.getTypePeriode();
    this.modeNotation = cycle.getModeNotation();
    this.baseMoyenne = cycle.getBaseMoyenne();
  }

  public static ParametresCycleAnnee figer(UUID anneeId, Cycle cycle) {
    return new ParametresCycleAnnee(anneeId, cycle);
  }

  public UUID getAnneeId() {
    return anneeId;
  }

  public UUID getCycleId() {
    return cycleId;
  }

  public TypePeriode getTypePeriode() {
    return typePeriode;
  }

  public ModeNotation getModeNotation() {
    return modeNotation;
  }

  public int getBaseMoyenne() {
    return baseMoyenne;
  }
}
