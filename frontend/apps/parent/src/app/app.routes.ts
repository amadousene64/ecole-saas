import { Route } from '@angular/router';
import { gardeAuthentifie, gardePage, ROUTES_SESSION } from '@ecole/auth';

/** Un compte rattaché à aucune école va activer son compte avec le code remis par l'école. */
const OPTIONS = { sansEcole: '/activer' };

/**
 * Toutes les pages sont chargées à la demande : le lot initial (budget 450 Ko) ne porte que la
 * coquille, l'authentification et le routeur. Le service worker précharge ensuite ces fichiers.
 */
export const appRoutes: Route[] = [
  ...ROUTES_SESSION,
  {
    path: '',
    pathMatch: 'full',
    canActivate: [gardePage('accueil-parent', OPTIONS)],
    loadComponent: () => import('./accueil').then((m) => m.AccueilParent),
    title: 'Accueil · Espace parent',
  },
  {
    // Avant l'activation, le compte n'a aucun rôle dans l'école : il suffit d'être connecté.
    path: 'activer',
    canActivate: [gardeAuthentifie],
    loadComponent: () => import('./activation').then((m) => m.PageActivation),
    title: 'Activer mon compte · Espace parent',
  },
  {
    path: 'enfants',
    canActivate: [gardePage('enfants', OPTIONS)],
    loadComponent: () => import('./enfants').then((m) => m.PageEnfants),
    title: 'Mes enfants · Espace parent',
  },
  { path: '**', redirectTo: '' },
];
