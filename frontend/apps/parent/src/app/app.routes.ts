import { Route } from '@angular/router';
import { gardeRoles, ROUTES_SESSION } from '@ecole/auth';
import { AccueilParent } from './accueil';
import { PageBientot } from './bientot';

const garde = gardeRoles('PARENT', 'ELEVE');

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
