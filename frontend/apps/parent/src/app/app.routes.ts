import { Route } from '@angular/router';
import { gardeAuthentifie, ROUTES_SESSION } from '@ecole/auth';
import { PageActivation } from './activation';
import { PageEnfants } from './enfants';
import { gardeParent } from './garde-parent';
import { AccueilParent } from './accueil';
import { PageBientot } from './bientot';

const garde = gardeParent;

export const appRoutes: Route[] = [
  ...ROUTES_SESSION,
  {
    path: '',
    pathMatch: 'full',
    canActivate: [garde],
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
    canActivate: [garde],
    component: PageEnfants,
    title: 'Mes enfants · Espace parent',
  },
  {
    path: 'notes',
    canActivate: [garde],
    component: PageBientot,
    data: { titre: 'Notes' },
    title: 'Notes · Espace parent',
  },
  {
    path: 'paiements',
    canActivate: [garde],
    component: PageBientot,
    data: { titre: 'Paiements' },
    title: 'Paiements · Espace parent',
  },
  {
    path: 'messages',
    canActivate: [garde],
    component: PageBientot,
    data: { titre: 'Messages' },
    title: 'Messages · Espace parent',
  },
  { path: '**', redirectTo: '' },
];
