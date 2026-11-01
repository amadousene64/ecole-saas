import { Routes } from '@angular/router';
import { gardeAuthentifie } from './gardes';

/** Pages communes de la session (à placer avant les routes protégées par rôle). */
export const ROUTES_SESSION: Routes = [
  {
    path: 'choix-ecole',
    canActivate: [gardeAuthentifie],
    loadComponent: () =>
      import('./pages/pages-session').then((m) => m.PageChoixEcole),
  },
  {
    path: 'aucun-espace',
    canActivate: [gardeAuthentifie],
    loadComponent: () =>
      import('./pages/pages-session').then((m) => m.PageAucunEspace),
  },
  {
    path: 'erreur',
    canActivate: [gardeAuthentifie],
    loadComponent: () =>
      import('./pages/pages-session').then((m) => m.PageErreur),
  },
];
