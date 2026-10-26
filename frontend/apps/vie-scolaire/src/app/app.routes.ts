import { Route } from '@angular/router';
import { gardeRoles, PageMoi, ROUTES_SESSION } from '@ecole/auth';

export const appRoutes: Route[] = [
  ...ROUTES_SESSION,
  {
    path: '',
    pathMatch: 'full',
    canActivate: [
      gardeRoles('DIRECTEUR', 'CENSEUR', 'ENSEIGNANT', 'SURVEILLANT'),
    ],
    component: PageMoi,
  },
  { path: '**', redirectTo: '' },
];
