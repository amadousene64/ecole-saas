package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Niveau d'études (CI à Terminale), rattaché à un cycle pour toute sa vie. */
@Entity
@Table(name = "niveau")
public class Niveau extends TenantAwareEntity {

  @Column(name = "cycle_id", nullable = false, updatable = false)
  private UUID cycleId;

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 80)
  private String libelle;

  @Column(nullable = false)
  private int ordre;

  /** {@code null} : le niveau suit la base de son cycle. */
  @Column(name = "base_moyenne")
  private Integer baseMoyenne;

  @Column(nullable = false)
  private boolean actif = true;

  protected Niveau() {}

  private Niveau(UUID cycleId, String code, String libelle, int ordre, Integer baseMoyenne) {
    this.cycleId = Objects.requireNonNull(cycleId, "cycleId");
    this.code = Verif.code(code, "Le code");
    modifier(libelle, ordre, baseMoyenne, true);
  }

  public static Niveau creer(
      UUID cycleId, String code, String libelle, int ordre, Integer baseMoyenne) {
    return new Niveau(cycleId, code, libelle, ordre, baseMoyenne);
  }

  public void modifier(String libelle, int ordre, Integer baseMoyenne, boolean actif) {
    this.libelle = Verif.exiger(libelle, "Le libellé", 80);
    if (ordre < 1) {
      throw Verif.invalide("CHAMP_INVALIDE", "L'ordre commence à 1.");
    }
    this.ordre = ordre;
    this.baseMoyenne = baseMoyenne == null ? null : Cycle.base(baseMoyenne);
    this.actif = actif;
  }

  /** Base de la moyenne applicable : la surcharge du niveau, sinon celle du cycle. */
  public int baseMoyenneEffective(Cycle cycle) {
    return baseMoyenne != null ? baseMoyenne : cycle.getBaseMoyenne();
  }

  public boolean aLaBase(Integer baseMoyenne) {
    return Objects.equals(this.baseMoyenne, baseMoyenne);
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public UUID getCycleId() {
    return cycleId;
  }

  public String getCode() {
    return code;
  }

  public String getLibelle() {
    return libelle;
  }

  public int getOrdre() {
    return ordre;
  }

  public Integer getBaseMoyenne() {
    return baseMoyenne;
  }

  public boolean isActif() {
    return actif;
  }
}
