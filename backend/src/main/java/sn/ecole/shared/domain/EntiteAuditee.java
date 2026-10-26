package sn.ecole.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Clé UUID v7, colonnes d'audit, suppression logique et verrou optimiste. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntiteAuditee {

  @Id private UUID id = Identifiants.uuidV7();

  @CreatedDate
  @Column(name = "cree_le", nullable = false, updatable = false)
  private Instant creeLe;

  @CreatedBy
  @Column(name = "cree_par", updatable = false)
  private UUID creePar;

  @LastModifiedDate
  @Column(name = "modifie_le")
  private Instant modifieLe;

  @LastModifiedBy
  @Column(name = "modifie_par")
  private UUID modifiePar;

  @Column(name = "supprime_le")
  private Instant supprimeLe;

  /**
   * {@code null} tant que l'entité n'est pas persistée : Spring Data la sait alors « nouvelle ».
   */
  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  public UUID getId() {
    return id;
  }

  public Instant getCreeLe() {
    return creeLe;
  }

  public UUID getCreePar() {
    return creePar;
  }

  public Instant getModifieLe() {
    return modifieLe;
  }

  public UUID getModifiePar() {
    return modifiePar;
  }

  public Instant getSupprimeLe() {
    return supprimeLe;
  }

  public boolean estSupprime() {
    return supprimeLe != null;
  }

  /** Suppression logique : les données scolaires ne sont jamais supprimées physiquement. */
  protected void marquerSupprime(Instant quand) {
    this.supprimeLe = quand;
  }

  public Integer getVersion() {
    return version;
  }

  @Override
  public final boolean equals(Object autre) {
    if (this == autre) {
      return true;
    }
    return autre instanceof EntiteAuditee e
        && org.hibernate.Hibernate.getClass(this) == org.hibernate.Hibernate.getClass(e)
        && id.equals(e.id);
  }

  @Override
  public final int hashCode() {
    return id.hashCode();
  }
}
