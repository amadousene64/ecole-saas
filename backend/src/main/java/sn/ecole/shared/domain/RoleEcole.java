package sn.ecole.shared.domain;

/**
 * Rôles propres à une école (dossier de conception, §5). Ils ne sont jamais portés par le jeton
 * Keycloak : la source de vérité est la table {@code appartenance}.
 */
public enum RoleEcole {
  DIRECTEUR,
  CENSEUR,
  SECRETARIAT,
  SURVEILLANT,
  ENSEIGNANT,
  COMPTABLE,
  PARENT,
  ELEVE;

  /** Autorité Spring Security correspondante, utilisable dans {@code hasRole('...')}. */
  public String autorite() {
    return "ROLE_" + name();
  }
}
