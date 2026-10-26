package sn.ecole.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

/**
 * Base de toute entité métier.
 *
 * <ul>
 *   <li>{@code tenant_id} est renseigné et filtré automatiquement par Hibernate ({@link TenantId})
 *       à partir de {@link ContexteTenant} ; PostgreSQL applique en plus la politique RLS {@code
 *       isolement_ecole}.
 *   <li>Les lignes supprimées logiquement ({@code supprime_le} renseigné) sont exclues
 *       automatiquement de toutes les lectures JPA (requêtes, recherche par id, associations).
 * </ul>
 */
@MappedSuperclass
@SQLRestriction("supprime_le IS NULL")
public abstract class TenantAwareEntity extends EntiteAuditee {

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  public UUID getTenantId() {
    return tenantId;
  }
}
