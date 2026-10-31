import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { catchError, switchMap, take, throwError } from 'rxjs';
import { EcoleCourante } from './ecole-courante';
import { Session } from './session';

/** Seul appel de l'API qui se fait sans école (liste des écoles pour le sélecteur). */
const CHEMIN_SANS_ECOLE = '/api/v1/moi/ecoles';

let reconnexionEnCours = false;

/**
 * Ajoute le jeton et l'école demandée aux appels vers NOTRE API (même origine, chemin `/api`).
 * Rien n'est ajouté aux autres adresses : le jeton ne doit jamais partir vers un tiers.
 */
export const intercepteurApi: HttpInterceptorFn = (req, next) => {
  const url = urlApi(req.url);
  if (!url) {
    return next(req);
  }
  const oidc = inject(OidcSecurityService);
  const courante = inject(EcoleCourante);
  const session = inject(Session);
  const router = inject(Router);

  return oidc.getAccessToken().pipe(
    take(1),
    switchMap((jeton) => {
      let entetes = req.headers;
      if (jeton) {
        entetes = entetes.set('Authorization', `Bearer ${jeton}`);
      }
      const ecole = courante.id();
      // Un appel qui désigne lui-même son école (« Mes enfants », école par école) est respecté ;
      // le serveur vérifie de toute façon l'appartenance à celle-ci.
      if (
        ecole &&
        url.pathname !== CHEMIN_SANS_ECOLE &&
        !req.headers.has('X-Tenant-Id')
      ) {
        entetes = entetes.set('X-Tenant-Id', ecole);
      }
      return next(req.clone({ headers: entetes }));
    }),
    catchError((erreur: unknown) => {
      if (erreur instanceof HttpErrorResponse) {
        if (erreur.status === 401 && !reconnexionEnCours) {
          reconnexionEnCours = true;
          oidc.authorize();
        } else if (
          erreur.status === 403 &&
          erreur.error?.code === 'TENANT_NON_AUTORISE'
        ) {
          session.surEcoleRefusee();
          void router.navigateByUrl('/choix-ecole');
        }
      }
      return throwError(() => erreur);
    }),
  );
};

function urlApi(adresse: string): URL | null {
  try {
    const url = new URL(adresse, window.location.origin);
    const memeOrigine = url.origin === window.location.origin;
    const cheminApi =
      url.pathname === '/api' || url.pathname.startsWith('/api/');
    return memeOrigine && cheminApi ? url : null;
  } catch {
    return null;
  }
}
