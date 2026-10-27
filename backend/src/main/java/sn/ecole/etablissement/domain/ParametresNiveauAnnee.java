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
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Copie de la base de moyenne effective d'un niveau, prise à l'ouverture d'une année (surcharge du
 * niveau ou, à défaut, base de son cycle). Jamais modifiée ensuite.
 */
@Entity
@Immutable
@Table(name = "parametres_niveau_annee")
public class ParametresNiveauAnnee extends TenantAwareEntity {

  @Column(name = "annee_id", nullable = false, updatable = false)
  private UUID anneeId;

  @Column(name = "niveau_id", nullable = false, updatable = false)
  private UUID niveauId;

  @Column(name = "cycle_id", nullable = false, updatable = false)
  private UUID cycleId;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode_notation", nullable = false, updatable = false, length = 12)
  private ModeNotation modeNotation;

  @Column(name = "base_moyenne", nullable = false, updatable = false)
  private int baseMoyenne;

  protected ParametresNiveauAnnee() {}

  private ParametresNiveauAnnee(UUID anneeId, Niveau niveau, Cycle cycle) {
    this.anneeId = Objects.requireNonNull(anneeId, "anneeId");
    this.niveauId = niveau.getId();
    this.cycleId = cycle.getId();
    this.modeNotation = cycle.getModeNotation();
    this.baseMoyenne = niveau.baseMoyenneEffective(cycle);
  }

  public static ParametresNiveauAnnee figer(UUID anneeId, Niveau niveau, Cycle cycle) {
    return new ParametresNiveauAnnee(anneeId, niveau, cycle);
  }

  public UUID getAnneeId() {
    return anneeId;
  }

  public UUID getNiveauId() {
    return niveauId;
  }

  public UUID getCycleId() {
    return cycleId;
  }

  public ModeNotation getModeNotation() {
    return modeNotation;
  }

  public int getBaseMoyenne() {
    return baseMoyenne;
  }
}
