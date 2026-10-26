package sn.ecole.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Une école cliente de la plateforme. Table de référence de la plateforme : elle n'a pas de {@code
 * tenant_id} (c'est le tenant lui-même) et l'application ne peut que la lire.
 */
@Entity
@Table(name = "tenant")
public class Tenant extends EntiteAuditee {

  @Column(nullable = false, updatable = false)
  private String code;

  @Column(nullable = false)
  private String nom;

  @Column(name = "sous_domaine", nullable = false)
  private String sousDomaine;

  @Column(nullable = false)
  private boolean actif;

  protected Tenant() {}

  public String getCode() {
    return code;
  }

  public String getNom() {
    return nom;
  }

  public String getSousDomaine() {
    return sousDomaine;
  }

  public boolean isActif() {
    return actif;
  }
}
