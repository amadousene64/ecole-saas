package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Salle de l'école, indépendante de l'année scolaire. */
@Entity
@Table(name = "salle")
public class Salle extends TenantAwareEntity {

  @Column(nullable = false, length = 60)
  private String nom;

  @Column(nullable = false)
  private int capacite;

  @Column(nullable = false)
  private boolean actif = true;

  protected Salle() {}

  private Salle(String nom, int capacite) {
    modifier(nom, capacite, true);
  }

  public static Salle creer(String nom, int capacite) {
    return new Salle(nom, capacite);
  }

  public void modifier(String nom, int capacite, boolean actif) {
    this.nom = Verif.exiger(nom, "Le nom", 60);
    this.capacite = Classe.capacite(capacite);
    this.actif = actif;
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public String getNom() {
    return nom;
  }

  public int getCapacite() {
    return capacite;
  }

  public boolean isActif() {
    return actif;
  }
}
