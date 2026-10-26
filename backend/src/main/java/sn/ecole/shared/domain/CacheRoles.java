package sn.ecole.shared.domain;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * Cache des rôles d'un utilisateur dans une école, versionné par utilisateur : chaque changement de
 * droits passe à une nouvelle génération, et une entrée écrite pour une génération périmée n'est
 * plus jamais lue. Un cache vide ou en panne n'est jamais une source de droits : on retombe alors
 * sur la table {@code appartenance}.
 */
public interface CacheRoles {

  /** Génération courante des droits de l'utilisateur ; vide si le cache est indisponible. */
  OptionalLong generation(UUID utilisateurId);

  /** Vide si absent du cache ; un ensemble vide signifie « connu : aucun rôle ». */
  Optional<Set<RoleEcole>> lire(UUID tenantId, UUID utilisateurId, long generation);

  /** Écrit les rôles lus en base APRÈS avoir lu {@code generation}. */
  void ecrire(UUID tenantId, UUID utilisateurId, long generation, Set<RoleEcole> roles);

  /**
   * Passe à une nouvelle génération : toutes les entrées existantes de l'utilisateur, dans toutes
   * les écoles, deviennent inutilisables.
   *
   * @throws RuntimeException si le cache est injoignable (l'appelant doit alors annuler le
   *     changement de droits plutôt que de laisser des droits périmés en cache).
   */
  void nouvelleGeneration(UUID utilisateurId);
}
