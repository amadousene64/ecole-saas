/** Rôles du backend (enum RoleEcole). */
export type RoleEcole =
  | 'DIRECTEUR'
  | 'CENSEUR'
  | 'SECRETARIAT'
  | 'SURVEILLANT'
  | 'ENSEIGNANT'
  | 'COMPTABLE'
  | 'PARENT'
  | 'ELEVE';

/** Les trois applications. */
export type IdApp = 'backoffice' | 'vie-scolaire' | 'parent';

/**
 * Priorité des rôles : quand un utilisateur en a plusieurs dans la même école, le premier de cette
 * liste décide de son application et de son accueil.
 */
export const ORDRE_ROLES: readonly RoleEcole[] = [
  'DIRECTEUR',
  'SECRETARIAT',
  'CENSEUR',
  'COMPTABLE',
  'SURVEILLANT',
  'ENSEIGNANT',
  'PARENT',
  'ELEVE',
];

/** Application où travaille chaque rôle. */
export const APP_DU_ROLE: Readonly<Record<RoleEcole, IdApp>> = {
  DIRECTEUR: 'backoffice',
  SECRETARIAT: 'backoffice',
  CENSEUR: 'backoffice',
  COMPTABLE: 'backoffice',
  SURVEILLANT: 'vie-scolaire',
  ENSEIGNANT: 'vie-scolaire',
  PARENT: 'parent',
  ELEVE: 'parent',
};

export const LIBELLES_ROLES: Readonly<Record<RoleEcole, string>> = {
  DIRECTEUR: 'Directeur',
  SECRETARIAT: 'Secrétariat',
  CENSEUR: 'Censeur',
  COMPTABLE: 'Comptable',
  SURVEILLANT: 'Surveillant',
  ENSEIGNANT: 'Enseignant',
  PARENT: 'Parent',
  ELEVE: 'Élève',
};

export const IDS_APPS: readonly IdApp[] = [
  'backoffice',
  'vie-scolaire',
  'parent',
];

export function estRole(valeur: string): valeur is RoleEcole {
  return (ORDRE_ROLES as readonly string[]).includes(valeur);
}

export function estIdApp(valeur: string | null | undefined): valeur is IdApp {
  return !!valeur && (IDS_APPS as readonly string[]).includes(valeur);
}

/** Rôles connus de la liste reçue du serveur, dans l'ordre de priorité. */
export function rolesConnus(roles: readonly string[]): RoleEcole[] {
  return ORDRE_ROLES.filter((r) => roles.includes(r));
}

/** Rôle qui décide de l'application et de l'accueil ; null si aucun rôle connu. */
export function rolePrincipal(roles: readonly string[]): RoleEcole | null {
  return rolesConnus(roles)[0] ?? null;
}

/** Application du rôle principal ; null si aucun rôle connu. */
export function appPour(roles: readonly string[]): IdApp | null {
  const principal = rolePrincipal(roles);
  return principal ? APP_DU_ROLE[principal] : null;
}
