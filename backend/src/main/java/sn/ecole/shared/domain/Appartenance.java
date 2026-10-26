package sn.ecole.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Un utilisateur (identifié par le {@code sub} Keycloak) a un rôle dans une école. Source de vérité
 * des droits : une même personne peut être enseignante dans une école et parent dans une autre, et
 * n'a dans chaque école que les rôles qui y sont enregistrés.
 */
@Entity
@Table(name = "appartenance")
public class Appartenance extends TenantAwareEntity {

  @Column(name = "utilisateur_id", nullable = false, updatable = false)
  private UUID utilisateurId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private RoleEcole role;

  protected Appartenance() {}

  private Appartenance(UUID utilisateurId, RoleEcole role) {
    this.utilisateurId = Objects.requireNonNull(utilisateurId, "utilisateurId");
    this.role = Objects.requireNonNull(role, "role");
  }

  /** L'école est celle du contexte courant (renseignée par Hibernate à l'enregistrement). */
  public static Appartenance attribuer(UUID utilisateurId, RoleEcole role) {
    return new Appartenance(utilisateurId, role);
  }

  public UUID getUtilisateurId() {
    return utilisateurId;
  }

  public RoleEcole getRole() {
    return role;
  }
}
