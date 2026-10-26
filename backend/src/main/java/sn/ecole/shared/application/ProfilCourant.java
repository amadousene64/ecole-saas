package sn.ecole.shared.application;

import java.util.Set;
import java.util.UUID;
import sn.ecole.shared.domain.RoleEcole;

/** Utilisateur connecté, école courante et ses rôles dans cette école. */
public record ProfilCourant(UUID utilisateurId, UUID tenantId, Set<RoleEcole> roles) {

  public ProfilCourant {
    roles = Set.copyOf(roles);
  }
}
