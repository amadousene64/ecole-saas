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

  private ParametresNiveauAnnee(
      UUID anneeId, UUID niveauId, UUID cycleId, ModeNotation mode, int baseMoyenne) {
    this.anneeId = Objects.requireNonNull(anneeId, "anneeId");
    this.niveauId = niveauId;
    this.cycleId = cycleId;
    this.modeNotation = mode;
    this.baseMoyenne = baseMoyenne;
  }

  /** Copie à l'ouverture d'une année : à partir du modèle du cycle, tel qu'il est à cet instant. */
  public static ParametresNiveauAnnee figer(UUID anneeId, Niveau niveau, Cycle cycle) {
    return new ParametresNiveauAnnee(
        anneeId,
        niveau.getId(),
        cycle.getId(),
        cycle.getModeNotation(),
        niveau.baseMoyenneEffective(cycle));
  }

  /**
   * Niveau créé alors que l'année est déjà ouverte : il se fige avec les paramètres FIGÉS du cycle
   * de cette année (pas avec le modèle, qui a pu changer depuis l'ouverture).
   */
  public static ParametresNiveauAnnee figer(
      UUID anneeId, Niveau niveau, ParametresCycleAnnee cycleFige) {
    int base =
        niveau.getBaseMoyenne() != null ? niveau.getBaseMoyenne() : cycleFige.getBaseMoyenne();
    return new ParametresNiveauAnnee(
        anneeId, niveau.getId(), cycleFige.getCycleId(), cycleFige.getModeNotation(), base);
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
