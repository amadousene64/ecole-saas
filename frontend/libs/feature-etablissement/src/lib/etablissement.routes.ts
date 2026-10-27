import { Route } from '@angular/router';
import { LienNavigation } from '@ecole/ui';

/** Écrans du module établissement, à monter sous `/etablissement` (chargement à la demande). */
export const ROUTES_ETABLISSEMENT: Route[] = [
  {
    path: 'parametres',
    title: "Paramètres de l'établissement",
    loadComponent: () =>
      import('./parametres/page-parametres').then((m) => m.PageParametres),
  },
  {
    path: 'structure',
    title: 'Structure pédagogique',
    loadComponent: () =>
      import('./structure/page-structure').then((m) => m.PageStructure),
  },
  {
    path: 'annees',
    title: 'Années scolaires et périodes',
    loadComponent: () =>
      import('./annees/page-annees').then((m) => m.PageAnnees),
  },
  {
    path: 'classes',
    title: 'Classes et salles',
    loadComponent: () =>
      import('./classes/page-classes').then((m) => m.PageClasses),
  },
  { path: '', pathMatch: 'full', redirectTo: 'parametres' },
];

/** Entrées de navigation correspondantes. */
export const LIENS_ETABLISSEMENT: LienNavigation[] = [
  { chemin: '/etablissement/parametres', libelle: 'Établissement' },
  { chemin: '/etablissement/structure', libelle: 'Structure pédagogique' },
  { chemin: '/etablissement/annees', libelle: 'Années et périodes' },
  { chemin: '/etablissement/classes', libelle: 'Classes et salles' },
];
