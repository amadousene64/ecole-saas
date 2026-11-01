import { Route } from '@angular/router';
import { gardePage, ROUTES_SESSION } from '@ecole/auth';
import { ROUTES_ETABLISSEMENT } from '@ecole/feature-etablissement';
import { ROUTES_INSCRIPTION } from '@ecole/feature-inscription';
import { AccueilBackoffice } from './accueil';

/**
 * Routes du back-office. Chaque route est gardée par sa page de la table des droits : un rôle qui
 * n'a pas le droit est ramené à son accueil (ou renvoyé dans sa propre application).
 */
export const appRoutes: Route[] = [
  ...ROUTES_SESSION,
  {
    path: '',
    pathMatch: 'full',
    canActivate: [gardePage('accueil-backoffice')],
    component: AccueilBackoffice,
    title: 'Accueil',
  },
  { path: 'eleves', children: ROUTES_INSCRIPTION },
  { path: 'etablissement', children: ROUTES_ETABLISSEMENT },
  { path: '**', redirectTo: '' },
];
