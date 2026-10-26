import { Route } from '@angular/router';
import { gardeRoles, PageMoi, ROUTES_SESSION } from '@ecole/auth';

export const appRoutes: Route[] = [
  ...ROUTES_SESSION,
  {
    path: '',
    pathMatch: 'full',
    canActivate: [gardeRoles('PARENT')],
    component: PageMoi,
  },
  { path: '**', redirectTo: '' },
];
