package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Jour férié (un jour : début = fin) ou période de vacances d'une année scolaire. */
@Entity
@Table(name = "evenement_calendrier")
public class EvenementCalendrier extends TenantAwareEntity {

  @Column(name = "annee_id", nullable = false, updatable = false)
  private UUID anneeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TypeEvenement type;

  @Column(nullable = false, length = 120)
  private String libelle;

  @Column(name = "date_debut", nullable = false)
  private LocalDate dateDebut;

  @Column(name = "date_fin", nullable = false)
  private LocalDate dateFin;

  protected EvenementCalendrier() {}

  private EvenementCalendrier(
      UUID anneeId, TypeEvenement type, String libelle, LocalDate debut, LocalDate fin) {
    this.anneeId = Objects.requireNonNull(anneeId, "anneeId");
    modifier(type, libelle, debut, fin);
  }

  public static EvenementCalendrier creer(
      UUID anneeId, TypeEvenement type, String libelle, LocalDate debut, LocalDate fin) {
    return new EvenementCalendrier(anneeId, type, libelle, debut, fin);
  }

  public void modifier(TypeEvenement type, String libelle, LocalDate debut, LocalDate fin) {
    this.type = Objects.requireNonNull(type, "type");
    this.libelle = Verif.exiger(libelle, "Le libellé", 120);
    this.dateDebut = Objects.requireNonNull(debut, "debut");
    this.dateFin = Objects.requireNonNull(fin, "fin");
    if (debut.isAfter(fin)) {
      throw Verif.regle("EVENEMENT_INVALIDE", "L'événement finit avant d'avoir commencé.");
    }
  }

  public boolean chevauche(LocalDate debut, LocalDate fin) {
    return !dateFin.isBefore(debut) && !dateDebut.isAfter(fin);
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public UUID getAnneeId() {
    return anneeId;
  }

  public TypeEvenement getType() {
    return type;
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
