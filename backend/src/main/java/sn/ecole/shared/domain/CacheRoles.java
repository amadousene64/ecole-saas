package sn.ecole.shared.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cache des rôles d'un utilisateur dans une école. Un cache vide ou en panne n'est jamais une
 * source de droits : on retombe alors sur la table {@code appartenance}.
 */
public interface CacheRoles {

  /** Vide si absent du cache ; un ensemble vide signifie « connu : aucun rôle ». */
  Optional<Set<RoleEcole>> lire(UUID tenantId, UUID utilisateurId);

  void ecrire(UUID tenantId, UUID utilisateurId, Set<RoleEcole> roles);

  void invalider(UUID tenantId, UUID utilisateurId);
}
