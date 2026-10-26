package sn.ecole.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base de toute entité métier. {@code tenant_id} est renseigné et filtré automatiquement par
 * Hibernate ({@link TenantId}) à partir de {@link ContexteTenant} ; PostgreSQL applique en plus la
 * politique RLS {@code isolement_ecole}.
 */
@MappedSuperclass
public abstract class TenantAwareEntity extends EntiteAuditee {

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  public UUID getTenantId() {
    return tenantId;
  }
}
