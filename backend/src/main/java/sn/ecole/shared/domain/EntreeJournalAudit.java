package sn.ecole.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entrée du journal d'audit : qui ({@code acteurId}), quand ({@code creeLe}), quoi ({@code action}
 * sur {@code typeCible}/{@code idCible}, avec des détails) et dans quelle école ({@code tenantId}).
 * Immuable : jamais modifiée ni supprimée (et la base l'interdit à l'application).
 */
@Entity
@Immutable
@Table(name = "journal_audit")
public class EntreeJournalAudit extends TenantAwareEntity {

  @Column(name = "acteur_id", nullable = false, updatable = false)
  private UUID acteurId;

  @Column(nullable = false, updatable = false)
  private String action;

  @Column(name = "type_cible", nullable = false, updatable = false)
  private String typeCible;

  @Column(name = "id_cible", nullable = false, updatable = false)
  private UUID idCible;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false)
  private String details;

  protected EntreeJournalAudit() {}

  public EntreeJournalAudit(
      UUID acteurId, String action, String typeCible, UUID idCible, String detailsJson) {
    this.acteurId = Objects.requireNonNull(acteurId, "acteurId");
    this.action = Objects.requireNonNull(action, "action");
    this.typeCible = Objects.requireNonNull(typeCible, "typeCible");
    this.idCible = Objects.requireNonNull(idCible, "idCible");
    this.details = Objects.requireNonNull(detailsJson, "details");
  }

  public UUID getActeurId() {
    return acteurId;
  }

  public String getAction() {
    return action;
  }

  public String getTypeCible() {
    return typeCible;
  }

  public UUID getIdCible() {
    return idCible;
  }

  public String getDetails() {
    return details;
  }
}
