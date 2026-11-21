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
  // Assistant d'inscription en écran plein : `nouveau` ouvre un brouillon, puis l'adresse devient
  // `brouillons/:brouillonId` (reprise après rechargement ou depuis « Brouillons en cours »).
  {
    path: 'nouveau',
    title: 'Nouvelle inscription',
    canActivate: [gardePage('eleves-nouveau')],
    data: { pleinEcran: true },
    loadComponent: () =>
      import('./assistant/page-assistant-inscription').then(
        (m) => m.PageAssistantInscription,
      ),
  },
  {
    path: 'brouillons/:brouillonId',
    title: 'Nouvelle inscription',
    canActivate: [gardePage('eleves-nouveau')],
    data: { pleinEcran: true },
    loadComponent: () =>
      import('./assistant/page-assistant-inscription').then(
        (m) => m.PageAssistantInscription,
      ),
  },
  {
    path: 'import',
    title: "Import d'élèves",
    canActivate: [gardePage('import')],
    data: { pleinEcran: true },
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
