import { Route } from '@angular/router';
import { gardeRoles, PageMoi, ROUTES_SESSION } from '@ecole/auth';
import { ROUTES_ETABLISSEMENT } from '@ecole/feature-etablissement';
import { ROUTES_INSCRIPTION } from '@ecole/feature-inscription';

/** Rôles qui accèdent au back-office ; chaque écran masque ensuite ce que son rôle ne peut pas faire. */
const ROLES_BACKOFFICE = [
  'DIRECTEUR',
  'CENSEUR',
  'SECRETARIAT',
  'ENSEIGNANT',
  'COMPTABLE',
] as const;

export const appRoutes: Route[] = [
  ...ROUTES_SESSION,
  {
    path: '',
    pathMatch: 'full',
    canActivate: [gardeRoles(...ROLES_BACKOFFICE)],
    component: PageMoi,
  },
  {
    path: 'etablissement',
    canActivate: [gardeRoles(...ROLES_BACKOFFICE)],
    children: ROUTES_ETABLISSEMENT,
  },
  {
    path: 'eleves',
    // L'enseignant n'a aucun accès aux élèves pour l'instant (le serveur le refuse de toute façon).
    canActivate: [
      gardeRoles(
        'DIRECTEUR',
        'CENSEUR',
        'SECRETARIAT',
        'SURVEILLANT',
        'COMPTABLE',
      ),
    ],
    children: ROUTES_INSCRIPTION,
  },
  { path: '**', redirectTo: '' },
];
