import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Notifications } from '@ecole/ui';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import { IdApp } from './droits/roles';
import { gardeAuthentifie, gardePage } from './gardes';
import { CONFIG_APPS, RedirectionApp, URLS_DEV } from './redirection-app';
import { EtatSession, Session } from './session';

describe('gardes de route', () => {
  const oidc = { isAuthenticated: vi.fn(), authorize: vi.fn() };
  const redirection = {
    vers: vi.fn(),
    origine: vi.fn((): IdApp | null => null),
    oublierOrigine: vi.fn(),
  };

  function preparer(
    connecte: boolean,
    etat: EtatSession = 'pret',
    roles: string[] = [],
    app: IdApp = 'backoffice',
  ) {
    oidc.isAuthenticated.mockReturnValue(of(connecte));
    const session = {
      charger: vi.fn(() => Promise.resolve()),
      etat: () => etat,
      roles: () => roles,
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OidcSecurityService, useValue: oidc },
        { provide: Session, useValue: session },
        { provide: CONFIG_APPS, useValue: { courante: app, urls: URLS_DEV } },
        { provide: RedirectionApp, useValue: redirection },
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
    redirection.origine.mockReturnValue(null);
  });

  it("retire de l'adresse l'école et l'origine portées par un renvoi d'une autre application", async () => {
    const session = preparer(true, 'pret', ['SURVEILLANT'], 'vie-scolaire');
    const resultat = await executer(
      gardePage('accueil-vie-scolaire'),
      '/?ecole=01930000-0000-7000-8000-0000000000a1&depuis=backoffice&autre=1',
    );
    expect(adresse(resultat)).toBe('/?autre=1');
    // Redirection propre avant toute autre décision : rien n'a encore été chargé.
    expect(session.charger).not.toHaveBeenCalled();
    expect(redirection.vers).not.toHaveBeenCalled();
  });

  it("renvoie vers la connexion quand l'utilisateur n'est pas connecté, et mémorise la page", async () => {
    const session = preparer(false);
    expect(await executer(gardePage('eleves'), '/eleves')).toBe(false);
    expect(oidc.authorize).toHaveBeenCalled();
    expect(sessionStorage.getItem('ecole.retour')).toBe('/eleves');
    expect(session.charger).not.toHaveBeenCalled();
  });

  it('laisse passer un rôle qui a la page, dans sa propre application', async () => {
    preparer(true, 'pret', ['SECRETARIAT']);
    expect(await executer(gardePage('eleves'))).toBe(true);
    expect(redirection.vers).not.toHaveBeenCalled();
  });

  it('renvoie un utilisateur arrivé dans la mauvaise application vers la sienne', async () => {
    preparer(true, 'pret', ['SURVEILLANT'], 'backoffice');
    expect(await executer(gardePage('accueil-backoffice'), '/')).toBe(false);
    expect(redirection.vers).toHaveBeenCalledWith('vie-scolaire');
  });

  it.each([
    ['DIRECTEUR', 'backoffice'],
    ['ENSEIGNANT', 'vie-scolaire'],
    ['PARENT', 'parent'],
  ] as const)('un %s arrivé ailleurs va dans %s', async (role, cible) => {
    const autre: IdApp = cible === 'parent' ? 'backoffice' : 'parent';
    preparer(true, 'pret', [role], autre);
    await executer(gardePage('x'), '/');
    expect(redirection.vers).toHaveBeenCalledWith(cible);
  });

  it("ne tourne pas en boucle : revenu d'où il vient d'être renvoyé, il voit « aucun espace »", async () => {
    preparer(true, 'pret', ['SURVEILLANT'], 'backoffice');
    redirection.origine.mockReturnValue('vie-scolaire');
    expect(adresse(await executer(gardePage('x'), '/'))).toBe('/aucun-espace');
    expect(redirection.vers).not.toHaveBeenCalled();
  });

  it('un compte sans rôle connu dans cette école voit « aucun espace »', async () => {
    preparer(true, 'pret', []);
    expect(adresse(await executer(gardePage('eleves')))).toBe('/aucun-espace');
  });

  it("ramène à l'accueil, avec un message discret, une page que le rôle ne voit pas", async () => {
    preparer(true, 'pret', ['COMPTABLE']);
    const info = vi.spyOn(TestBed.inject(Notifications), 'info');
    expect(adresse(await executer(gardePage('import'), '/eleves/import'))).toBe(
      '/',
    );
    expect(info).toHaveBeenCalledOnce();
    expect(info.mock.calls[0][0]).toContain('pas disponible pour votre rôle');
  });

  it("ne renvoie pas à l'accueil en boucle si c'est l'accueil lui-même qui est refusé", async () => {
    preparer(true, 'pret', ['COMPTABLE']);
    expect(adresse(await executer(gardePage('import'), '/'))).toBe(
      '/aucun-espace',
    );
  });

  it("refuse une page dont le module n'est pas construit", async () => {
    preparer(true, 'pret', ['DIRECTEUR']);
    expect(adresse(await executer(gardePage('finances'), '/finances'))).toBe(
      '/',
    );
  });

  it("envoie vers le choix d'école (état choix-requis)", async () => {
    preparer(true, 'choix-requis');
    expect(adresse(await executer(gardePage('eleves')))).toBe('/choix-ecole');
  });

  it("aucune école : choix d'école par défaut, ou la page demandée par l'application", async () => {
    preparer(true, 'aucune-ecole');
    expect(adresse(await executer(gardePage('eleves')))).toBe('/choix-ecole');
    TestBed.resetTestingModule();
    preparer(true, 'aucune-ecole', [], 'parent');
    expect(
      adresse(await executer(gardePage('enfants', { sansEcole: '/activer' }))),
    ).toBe('/activer');
  });

  it.each<EtatSession>(['erreur', 'inconnu'])(
    "envoie vers la page d'erreur (état %s)",
    async (etat) => {
      preparer(true, etat);
      expect(adresse(await executer(gardePage('eleves')))).toBe('/erreur');
    },
  );

  it('revient à la page demandée avant la connexion', async () => {
    preparer(true, 'pret', ['DIRECTEUR']);
    sessionStorage.setItem('ecole.retour', '/eleves/12');
    expect(adresse(await executer(gardePage('accueil-backoffice'), '/'))).toBe(
      '/eleves/12',
    );
    expect(sessionStorage.getItem('ecole.retour')).toBeNull();
  });

  it("ignore une adresse de retour qui n'est pas interne", async () => {
    preparer(true, 'pret', ['DIRECTEUR']);
    sessionStorage.setItem('ecole.retour', '//tiers.example/piege');
    expect(await executer(gardePage('accueil-backoffice'), '/')).toBe(true);
  });

  it("gardeAuthentifie n'exige que la connexion", async () => {
    const session = preparer(true, 'choix-requis');
    expect(await executer(gardeAuthentifie)).toBe(true);
    expect(session.charger).not.toHaveBeenCalled();
  });
});
