package sn.ecole.personnel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Entrée de l'annuaire du personnel : prénoms et nom du compte, relevés à chaque présentation de
 * l'utilisateur à l'école. Un compte jamais connecté n'a pas d'entrée : on l'affiche par son
 * identifiant.
 */
@Entity
@Table(name = "membre_personnel")
public class MembrePersonnel extends TenantAwareEntity {

  @Column(name = "utilisateur_id", nullable = false, updatable = false)
  private UUID utilisateurId;

  @Column(length = 100)
  private String prenoms;

  @Column(length = 60)
  private String nom;

  @Column(name = "vu_le", nullable = false)
  private Instant vuLe;

  protected MembrePersonnel() {}

  public static MembrePersonnel creer(
      UUID utilisateurId, String prenoms, String nom, Instant vuLe) {
    MembrePersonnel m = new MembrePersonnel();
    m.utilisateurId = Objects.requireNonNull(utilisateurId);
    m.renseigner(prenoms, nom, vuLe);
    return m;
  }

  /** Vrai si les noms ont changé (le compte a été renommé). */
  public boolean renseigner(String prenoms, String nom, Instant vuLe) {
    String p = borne(prenoms, 100);
    String n = borne(nom, 60);
    boolean change = !Objects.equals(p, this.prenoms) || !Objects.equals(n, this.nom);
    this.prenoms = p;
    this.nom = n;
    this.vuLe = Objects.requireNonNull(vuLe);
    return change;
  }

  /** Mêmes noms, et vu depuis moins d'un jour : rien à écrire. */
  public boolean aJour(String prenoms, String nom, Instant maintenant) {
    return Objects.equals(borne(prenoms, 100), this.prenoms)
        && Objects.equals(borne(nom, 60), this.nom)
        && vuLe.isAfter(maintenant.minus(java.time.Duration.ofDays(1)));
  }

  private static String borne(String valeur, int max) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    String propre = valeur.strip();
    return propre.length() > max ? propre.substring(0, max) : propre;
  }

  public UUID getUtilisateurId() {
    return utilisateurId;
  }

  public String getPrenoms() {
    return prenoms;
  }

  public String getNom() {
    return nom;
  }

  public Instant getVuLe() {
    return vuLe;
  }
}
