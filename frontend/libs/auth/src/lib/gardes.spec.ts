import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import { gardeAuthentifie, gardeRoles } from './gardes';
import { EtatSession, Session } from './session';

describe('gardes de route', () => {
  const oidc = { isAuthenticated: vi.fn(), authorize: vi.fn() };

  function preparer(
    connecte: boolean,
    etat: EtatSession = 'pret',
    roles: string[] = [],
  ) {
    oidc.isAuthenticated.mockReturnValue(of(connecte));
    const session = {
      charger: vi.fn(() => Promise.resolve()),
      etat: () => etat,
      aUnRole: (requis: readonly string[]) =>
        requis.length === 0 || requis.some((r) => roles.includes(r)),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OidcSecurityService, useValue: oidc },
        { provide: Session, useValue: session },
      ],
    });
    return session;
  }

  function executer(garde: CanActivateFn, url = '/eleves') {
    return TestBed.runInInjectionContext(() =>
      garde({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as Promise<boolean | UrlTree>;
  }

  function adresse(resultat: boolean | UrlTree): string {
    expect(resultat).toBeInstanceOf(UrlTree);
    return TestBed.inject(Router).serializeUrl(resultat as UrlTree);
  }

  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it("renvoie vers la connexion quand l'utilisateur n'est pas connecté, et mémorise la page", async () => {
    const session = preparer(false);
    expect(await executer(gardeRoles('DIRECTEUR'), '/eleves')).toBe(false);
    expect(oidc.authorize).toHaveBeenCalled();
    expect(sessionStorage.getItem('ecole.retour')).toBe('/eleves');
    expect(session.charger).not.toHaveBeenCalled();
  });

  it('laisse passer quand le rôle exigé est présent dans /moi', async () => {
    preparer(true, 'pret', ['DIRECTEUR']);
    expect(await executer(gardeRoles('DIRECTEUR', 'SECRETARIAT'))).toBe(true);
  });

  it("laisse passer sans rôle exigé dès qu'une école est choisie", async () => {
    preparer(true, 'pret', []);
    expect(await executer(gardeRoles())).toBe(true);
  });

  it('refuse (page accès refusé) quand le rôle manque', async () => {
    preparer(true, 'pret', ['PARENT']);
    expect(adresse(await executer(gardeRoles('DIRECTEUR')))).toBe(
      '/acces-refuse',
    );
  });

  it.each<EtatSession>(['choix-requis', 'aucune-ecole'])(
    "envoie vers le choix d'école (état %s)",
    async (etat) => {
      preparer(true, etat);
      expect(adresse(await executer(gardeRoles('DIRECTEUR')))).toBe(
        '/choix-ecole',
      );
    },
  );

  it.each<EtatSession>(['erreur', 'inconnu'])(
    "envoie vers la page d'erreur (état %s)",
    async (etat) => {
      preparer(true, etat);
      expect(adresse(await executer(gardeRoles()))).toBe('/erreur');
    },
  );

  it('revient à la page demandée avant la connexion', async () => {
    preparer(true, 'pret', ['DIRECTEUR']);
    sessionStorage.setItem('ecole.retour', '/eleves/12');
    expect(adresse(await executer(gardeRoles('DIRECTEUR'), '/'))).toBe(
      '/eleves/12',
    );
    expect(sessionStorage.getItem('ecole.retour')).toBeNull();
  });

  it("ignore une adresse de retour qui n'est pas interne", async () => {
    preparer(true, 'pret', ['DIRECTEUR']);
    sessionStorage.setItem('ecole.retour', '//tiers.example/piege');
    expect(await executer(gardeRoles('DIRECTEUR'), '/')).toBe(true);
  });

  it("gardeAuthentifie n'exige que la connexion", async () => {
    const session = preparer(true, 'choix-requis');
    expect(await executer(gardeAuthentifie)).toBe(true);
    expect(session.charger).not.toHaveBeenCalled();
  });
});
