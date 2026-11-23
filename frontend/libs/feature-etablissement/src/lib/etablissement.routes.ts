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
    path: 'enseignements',
    title: 'Matières et horaires',
    canActivate: [gardePage('enseignements')],
    loadComponent: () =>
      import('./enseignements/page-enseignements').then(
        (m) => m.PageEnseignements,
      ),
  },
  {
    path: 'affectations',
    title: 'Affectations',
    canActivate: [gardePage('affectations')],
    loadComponent: () =>
      import('./affectations/page-affectations').then(
        (m) => m.PageAffectations,
      ),
  },
  {
    path: 'annees',
    title: 'Années scolaires et périodes',
    canActivate: [gardePage('annees')],
    loadComponent: () =>
      import('./annees/page-annees').then((m) => m.PageAnnees),
  },
  // Assistant de préparation d'une année, en écran plein (création, puis reprise par son id).
  {
    path: 'annees/preparation',
    title: 'Préparer une année scolaire',
    canActivate: [gardePage('annees')],
    data: { pleinEcran: true },
    loadComponent: () =>
      import('./annees/page-preparation-annee').then(
        (m) => m.PagePreparationAnnee,
      ),
  },
  {
    path: 'annees/preparation/:anneeId',
    title: 'Préparer une année scolaire',
    canActivate: [gardePage('annees')],
    data: { pleinEcran: true },
    loadComponent: () =>
      import('./annees/page-preparation-annee').then(
        (m) => m.PagePreparationAnnee,
      ),
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
