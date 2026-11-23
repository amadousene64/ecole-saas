package sn.ecole.shared.application;

import java.util.Set;
import java.util.UUID;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Événement en process : un utilisateur s'est présenté à son école (GET /api/v1/moi), avec les
 * prénoms et nom de son compte (jeton). Le module {@code personnel} en tient l'annuaire du
 * personnel, pour désigner les enseignants et surveillants à affecter.
 */
public record UtilisateurIdentifie(
    UUID tenantId, UUID utilisateurId, String prenoms, String nom, Set<RoleEcole> roles) {

  public UtilisateurIdentifie {
    roles = Set.copyOf(roles);
  }

  /** Aucun nom dans les journaux. */
  @Override
  public String toString() {
    return "UtilisateurIdentifie[" + tenantId + ", " + utilisateurId + ", ***]";
  }
}
