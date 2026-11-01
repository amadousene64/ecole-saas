import { Route } from '@angular/router';
import { gardePage, PageAccueil, ROUTES_SESSION } from '@ecole/auth';

export const appRoutes: Route[] = [
  ...ROUTES_SESSION,
  {
    path: '',
    pathMatch: 'full',
    canActivate: [gardePage('accueil-vie-scolaire')],
    component: PageAccueil,
    title: 'Accueil · Vie scolaire',
  },
  { path: '**', redirectTo: '' },
];
