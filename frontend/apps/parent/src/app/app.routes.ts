import { Route } from '@angular/router';
import { gardeAuthentifie, gardePage, ROUTES_SESSION } from '@ecole/auth';
import { PageActivation } from './activation';
import { AccueilParent } from './accueil';
import { PageEnfants } from './enfants';

/** Un compte rattaché à aucune école va activer son compte avec le code remis par l'école. */
const OPTIONS = { sansEcole: '/activer' };

export const appRoutes: Route[] = [
  ...ROUTES_SESSION,
  {
    path: '',
    pathMatch: 'full',
    canActivate: [gardePage('accueil-parent', OPTIONS)],
    component: AccueilParent,
    title: 'Accueil · Espace parent',
  },
  {
    // Avant l'activation, le compte n'a aucun rôle dans l'école : il suffit d'être connecté.
    path: 'activer',
    canActivate: [gardeAuthentifie],
    component: PageActivation,
    title: 'Activer mon compte · Espace parent',
  },
  {
    path: 'enfants',
    canActivate: [gardePage('enfants', OPTIONS)],
    component: PageEnfants,
    title: 'Mes enfants · Espace parent',
  },
  { path: '**', redirectTo: '' },
];
