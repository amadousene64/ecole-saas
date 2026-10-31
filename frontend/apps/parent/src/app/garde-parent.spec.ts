import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Session } from '@ecole/auth';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import { gardeParent } from './garde-parent';

function session(etat: string, roles: string[]) {
  return {
    etat: signal(etat),
    roles: signal(roles),
    charger: async () => undefined,
    aUnRole: (requis: string[]) => requis.some((r) => roles.includes(r)),
  };
}

async function executer(etat: string, roles: string[]) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: Session, useValue: session(etat, roles) },
      {
        provide: OidcSecurityService,
        useValue: { isAuthenticated: () => of(true), authorize: vi.fn() },
      },
    ],
  });
  sessionStorage.clear();
  const resultat = await TestBed.runInInjectionContext(() =>
    gardeParent(
      {} as ActivatedRouteSnapshot,
      { url: '/' } as RouterStateSnapshot,
    ),
  );
  return { resultat, routeur: TestBed.inject(Router) };
}

describe('gardeParent', () => {
  it('mène un compte sans école à l’activation', async () => {
    const { resultat, routeur } = await executer('aucune-ecole', []);
    expect(resultat).toBeInstanceOf(UrlTree);
    expect(routeur.serializeUrl(resultat as UrlTree)).toBe('/activer');
  });

  it('laisse entrer un parent', async () => {
    expect((await executer('pret', ['PARENT'])).resultat).toBe(true);
  });

  it('refuse toujours le personnel (accès refusé, pas activation)', async () => {
    const { resultat, routeur } = await executer('pret', ['DIRECTEUR']);
    expect(routeur.serializeUrl(resultat as UrlTree)).toBe('/acces-refuse');
  });
});
