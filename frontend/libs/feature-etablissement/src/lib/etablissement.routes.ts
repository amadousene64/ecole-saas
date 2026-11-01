import { Route } from '@angular/router';
import { gardePage } from '@ecole/auth';

/**
 * Écrans du module établissement, à monter sous `/etablissement` (chargement à la demande). Chaque
 * route est gardée par sa page de la table des droits (`@ecole/auth`), qui génère aussi le menu.
 */
export const ROUTES_ETABLISSEMENT: Route[] = [
  {
    path: 'parametres',
    title: "Paramètres de l'établissement",
    canActivate: [gardePage('etablissement')],
    loadComponent: () =>
      import('./parametres/page-parametres').then((m) => m.PageParametres),
  },
  {
    path: 'structure',
    title: 'Structure pédagogique',
    canActivate: [gardePage('structure')],
    loadComponent: () =>
      import('./structure/page-structure').then((m) => m.PageStructure),
  },
  {
    path: 'annees',
    title: 'Années scolaires et périodes',
    canActivate: [gardePage('annees')],
    loadComponent: () =>
      import('./annees/page-annees').then((m) => m.PageAnnees),
  },
  {
    path: 'classes',
    title: 'Classes et salles',
    canActivate: [gardePage('classes')],
    loadComponent: () =>
      import('./classes/page-classes').then((m) => m.PageClasses),
  },
  { path: '', pathMatch: 'full', redirectTo: 'parametres' },
];
