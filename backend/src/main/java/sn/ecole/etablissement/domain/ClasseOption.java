package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Une option d'enseignement proposée par une classe. */
@Entity
@Table(name = "classe_option")
public class ClasseOption extends TenantAwareEntity {

  @Column(name = "classe_id", nullable = false, updatable = false)
  private UUID classeId;

  @Column(name = "option_id", nullable = false, updatable = false)
  private UUID optionId;

  protected ClasseOption() {}

  private ClasseOption(UUID classeId, UUID optionId) {
    this.classeId = Objects.requireNonNull(classeId, "classeId");
    this.optionId = Objects.requireNonNull(optionId, "optionId");
  }

  public static ClasseOption proposer(UUID classeId, UUID optionId) {
    return new ClasseOption(classeId, optionId);
  }

  public void retirer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public UUID getClasseId() {
    return classeId;
  }

  public UUID getOptionId() {
    return optionId;
  }
}
