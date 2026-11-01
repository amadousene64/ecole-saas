import { Route } from '@angular/router';
import { gardePage } from '@ecole/auth';

/**
 * Écrans du module inscription, à monter sous `/eleves` (chargement à la demande). Chaque route est
 * gardée par sa page de la table des droits (`@ecole/auth`), qui génère aussi le menu.
 */
export const ROUTES_INSCRIPTION: Route[] = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Élèves',
    canActivate: [gardePage('eleves')],
    loadComponent: () =>
      import('./eleves/page-eleves').then((m) => m.PageEleves),
  },
  {
    path: 'nouveau',
    title: 'Nouvel élève',
    canActivate: [gardePage('eleves-nouveau')],
    loadComponent: () =>
      import('./eleves/page-nouvel-eleve').then((m) => m.PageNouvelEleve),
  },
  {
    path: 'import',
    title: "Import d'élèves",
    canActivate: [gardePage('import')],
    loadComponent: () =>
      import('./import/page-import').then((m) => m.PageImport),
  },
  {
    path: ':eleveId',
    title: "Fiche de l'élève",
    canActivate: [gardePage('eleves')],
    loadComponent: () => import('./fiche/page-fiche').then((m) => m.PageFiche),
  },
];
