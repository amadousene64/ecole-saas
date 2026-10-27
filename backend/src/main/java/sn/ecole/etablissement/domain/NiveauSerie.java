package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Une série est proposée par un niveau. Un niveau sans série associée n'a pas de série. */
@Entity
@Table(name = "niveau_serie")
public class NiveauSerie extends TenantAwareEntity {

  @Column(name = "niveau_id", nullable = false, updatable = false)
  private UUID niveauId;

  @Column(name = "serie_id", nullable = false, updatable = false)
  private UUID serieId;

  protected NiveauSerie() {}

  private NiveauSerie(UUID niveauId, UUID serieId) {
    this.niveauId = Objects.requireNonNull(niveauId, "niveauId");
    this.serieId = Objects.requireNonNull(serieId, "serieId");
  }

  public static NiveauSerie associer(UUID niveauId, UUID serieId) {
    return new NiveauSerie(niveauId, serieId);
  }

  public void retirer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public UUID getNiveauId() {
    return niveauId;
  }

  public UUID getSerieId() {
    return serieId;
  }
}
