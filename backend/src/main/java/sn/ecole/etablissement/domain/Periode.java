package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Trimestre ou semestre d'un cycle pour une année. Les règles qui portent sur l'ensemble des
 * périodes d'un cycle (bornes, chevauchements) sont dans {@link ReglesPeriodes}.
 */
@Entity
@Table(name = "periode")
public class Periode extends TenantAwareEntity {

  @Column(name = "annee_id", nullable = false, updatable = false)
  private UUID anneeId;

  @Column(name = "cycle_id", nullable = false, updatable = false)
  private UUID cycleId;

  @Column(nullable = false, updatable = false)
  private int numero;

  @Column(nullable = false, length = 40)
  private String libelle;

  @Column(name = "date_debut", nullable = false)
  private LocalDate dateDebut;

  @Column(name = "date_fin", nullable = false)
  private LocalDate dateFin;

  protected Periode() {}

  private Periode(UUID anneeId, UUID cycleId, PeriodePrevue prevue, String libelle) {
    this.anneeId = Objects.requireNonNull(anneeId, "anneeId");
    this.cycleId = Objects.requireNonNull(cycleId, "cycleId");
    this.numero = prevue.numero();
    modifier(libelle, prevue.debut(), prevue.fin());
  }

  public static Periode creer(UUID anneeId, UUID cycleId, PeriodePrevue prevue, String libelle) {
    return new Periode(anneeId, cycleId, prevue, libelle);
  }

  public void modifier(String libelle, LocalDate dateDebut, LocalDate dateFin) {
    this.libelle = Verif.exiger(libelle, "Le libellé", 40);
    this.dateDebut = Objects.requireNonNull(dateDebut, "dateDebut");
    this.dateFin = Objects.requireNonNull(dateFin, "dateFin");
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public PeriodePrevue enPrevue() {
    return new PeriodePrevue(numero, dateDebut, dateFin);
  }

  public UUID getAnneeId() {
    return anneeId;
  }

  public UUID getCycleId() {
    return cycleId;
  }

  public int getNumero() {
    return numero;
  }

  public String getLibelle() {
    return libelle;
  }

  public LocalDate getDateDebut() {
    return dateDebut;
  }

  public LocalDate getDateFin() {
    return dateFin;
  }
}
