import { Route } from '@angular/router';
import { LienNavigation } from '@ecole/ui';

/** Écrans du module inscription, à monter sous `/eleves` (chargement à la demande). */
export const ROUTES_INSCRIPTION: Route[] = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Élèves',
    loadComponent: () =>
      import('./eleves/page-eleves').then((m) => m.PageEleves),
  },
  {
    path: 'nouveau',
    title: 'Nouvel élève',
    loadComponent: () =>
      import('./eleves/page-nouvel-eleve').then((m) => m.PageNouvelEleve),
  },
  {
    path: 'import',
    title: "Import d'élèves",
    loadComponent: () =>
      import('./import/page-import').then((m) => m.PageImport),
  },
  {
    path: ':eleveId',
    title: "Fiche de l'élève",
    loadComponent: () => import('./fiche/page-fiche').then((m) => m.PageFiche),
  },
];

/** Entrées de navigation correspondantes. */
export const LIENS_INSCRIPTION: LienNavigation[] = [
  { chemin: '/eleves', libelle: 'Élèves' },
];
