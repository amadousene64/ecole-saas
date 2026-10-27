package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Classe d'une année scolaire (ex. TS2B) : un niveau, une série si le niveau en a, une capacité et
 * une salle. Nom unique par école et par année ; l'année ne change jamais.
 */
@Entity
@Table(name = "classe")
public class Classe extends TenantAwareEntity {

  @Column(name = "annee_id", nullable = false, updatable = false)
  private UUID anneeId;

  @Column(name = "niveau_id", nullable = false)
  private UUID niveauId;

  @Column(name = "serie_id")
  private UUID serieId;

  @Column(nullable = false, length = 30)
  private String nom;

  @Column(nullable = false)
  private int capacite;

  @Column(name = "salle_id")
  private UUID salleId;

  protected Classe() {}

  private Classe(
      UUID anneeId, UUID niveauId, UUID serieId, String nom, int capacite, UUID salleId) {
    this.anneeId = Objects.requireNonNull(anneeId, "anneeId");
    modifier(niveauId, serieId, nom, capacite, salleId);
  }

  /** La cohérence niveau/série est vérifiée avant par {@link ReglesClasse}. */
  public static Classe creer(
      UUID anneeId, UUID niveauId, UUID serieId, String nom, int capacite, UUID salleId) {
    return new Classe(anneeId, niveauId, serieId, nom, capacite, salleId);
  }

  public void modifier(UUID niveauId, UUID serieId, String nom, int capacite, UUID salleId) {
    this.niveauId = Objects.requireNonNull(niveauId, "niveauId");
    this.serieId = serieId;
    this.nom = Verif.exiger(nom, "Le nom", 30);
    this.capacite = capacite(capacite);
    this.salleId = salleId;
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  static int capacite(int valeur) {
    if (valeur < 1 || valeur > 500) {
      throw Verif.invalide("CAPACITE_INVALIDE", "La capacité est comprise entre 1 et 500.");
    }
    return valeur;
  }

  public UUID getAnneeId() {
    return anneeId;
  }

  public UUID getNiveauId() {
    return niveauId;
  }

  public UUID getSerieId() {
    return serieId;
  }

  public String getNom() {
    return nom;
  }

  public int getCapacite() {
    return capacite;
  }

  public UUID getSalleId() {
    return salleId;
  }
}
