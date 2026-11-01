import type { NomIcone } from '@ecole/ui';
import { ACTIONS, IdAction, PAGES, PageDef } from './table';
import { APP_DU_ROLE, IdApp, RoleEcole, appPour, rolesConnus } from './roles';

/** Entrée de menu calculée pour un utilisateur. */
export interface EntreeMenu {
  id: string;
  chemin: string;
  libelle: string;
  icone: NomIcone;
  /** Intitulé de section sous lequel l'entrée est rangée, s'il y en a un. */
  groupe: string | null;
}

/** Fonctions pures : aucune dépendance Angular, donc testables en matrice. */

function dansRoles(
  roles: readonly string[],
  autorises: readonly RoleEcole[],
): boolean {
  return autorises.some((r) => roles.includes(r));
}

export function page(id: string): PageDef | undefined {
  return PAGES.find((p) => p.id === id);
}

/** Vrai si la page existe, est construite, et si l'un des rôles peut l'ouvrir. */
export function pagePermise(id: string, roles: readonly string[]): boolean {
  const p = page(id);
  return !!p && p.disponible && dansRoles(roles, p.roles);
}

export function peut(action: IdAction, roles: readonly string[]): boolean {
  return dansRoles(roles, ACTIONS[action]);
}

/** Menu d'un utilisateur dans une application : l'union des pages permises à ses rôles. */
export function menuPour(
  roles: readonly string[],
  app: IdApp,
): readonly EntreeMenu[] {
  const connus = rolesConnus(roles);
  const principal = connus[0];
  return PAGES.filter(
    (p) =>
      p.app === app &&
      p.disponible &&
      dansRoles(connus, p.menu ?? p.roles) &&
      dansRoles(connus, p.roles),
  ).map((p) => {
    // Libellé et section du rôle le plus prioritaire qui voit cette entrée.
    const role =
      connus.find((r) => (p.menu ?? p.roles).includes(r)) ?? principal;
    return {
      id: p.id,
      chemin: p.chemin,
      libelle: p.libelles?.[role] ?? p.libelle,
      icone: p.icone,
      groupe: p.groupes?.[role] ?? null,
    };
  });
}

/**
 * Application vers laquelle envoyer cet utilisateur ; null s'il a un rôle dans l'application où il
 * se trouve (un directeur qui est aussi parent dans la même école peut rester dans l'espace
 * parent), ou s'il n'a aucun rôle connu.
 */
export function appAAtteindre(
  roles: readonly string[],
  appCourante: IdApp,
): IdApp | null {
  const connus = rolesConnus(roles);
  if (connus.some((r) => APP_DU_ROLE[r] === appCourante)) {
    return null;
  }
  return appPour(roles);
}
