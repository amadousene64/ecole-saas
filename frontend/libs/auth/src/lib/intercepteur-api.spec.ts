import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import { EcoleCourante } from './ecole-courante';
import { intercepteurApi } from './intercepteur-api';
import { Session } from './session';

describe('intercepteurApi', () => {
  const oidc = {
    getAccessToken: vi.fn(() => of('jeton-abc')),
    authorize: vi.fn(),
  };
  const session = { surEcoleRefusee: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  let http: HttpClient;
  let controle: HttpTestingController;
  let courante: EcoleCourante;

  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([intercepteurApi])),
        provideHttpClientTesting(),
        { provide: OidcSecurityService, useValue: oidc },
        { provide: Session, useValue: session },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    controle = TestBed.inject(HttpTestingController);
    courante = TestBed.inject(EcoleCourante);
  });

  afterEach(() => controle.verify());

  it("ajoute le jeton et l'école sur un appel /api", () => {
    courante.choisir('ecole-a');
    http.get('/api/v1/moi').subscribe();
    const req = controle.expectOne('/api/v1/moi');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jeton-abc');
    expect(req.request.headers.get('X-Tenant-Id')).toBe('ecole-a');
    req.flush({});
  });

  it("respecte l'école désignée par l'appel lui-même (Mes enfants, école par école)", () => {
    courante.choisir('ecole-a');
    http
      .get('/api/v1/moi/enfants', { headers: { 'X-Tenant-Id': 'ecole-b' } })
      .subscribe();
    const req = controle.expectOne('/api/v1/moi/enfants');
    expect(req.request.headers.get('X-Tenant-Id')).toBe('ecole-b');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jeton-abc');
    req.flush([]);
  });

  it("un refus d'école sur un appel qui désignait une autre école garde l'école courante", () => {
    courante.choisir('ecole-a');
    http
      .get('/api/v1/moi/enfants', { headers: { 'X-Tenant-Id': 'ecole-b' } })
      .subscribe({ error: () => undefined });
    controle
      .expectOne('/api/v1/moi/enfants')
      .flush(
        { code: 'TENANT_NON_AUTORISE' },
        { status: 403, statusText: 'Forbidden' },
      );
    expect(session.surEcoleRefusee).not.toHaveBeenCalled();
    expect(courante.id()).toBe('ecole-a');
  });

  it("n'envoie pas X-Tenant-Id pour la liste des écoles, mais envoie le jeton", () => {
    courante.choisir('ecole-a');
    http.get('/api/v1/moi/ecoles').subscribe();
    const req = controle.expectOne('/api/v1/moi/ecoles');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jeton-abc');
    expect(req.request.headers.has('X-Tenant-Id')).toBe(false);
    req.flush([]);
  });

  it("n'envoie pas X-Tenant-Id tant qu'aucune école n'est choisie", () => {
    http.get('/api/v1/moi').subscribe();
    const req = controle.expectOne('/api/v1/moi');
    expect(req.request.headers.has('X-Tenant-Id')).toBe(false);
    req.flush({});
  });

  it.each([
    'https://tiers.example/api/v1/moi',
    '//tiers.example/api/v1/moi',
    '/assets/config.json',
    '/apiary/x',
  ])('ne touche pas aux appels hors de notre API : %s', (adresse) => {
    courante.choisir('ecole-a');
    http.get(adresse).subscribe();
    const req = controle.expectOne(adresse);
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.headers.has('X-Tenant-Id')).toBe(false);
    req.flush({});
  });

  it("oublie l'école et revient au choix quand le serveur la refuse", () => {
    courante.choisir('ecole-a');
    http.get('/api/v1/moi').subscribe({ error: () => undefined });
    controle
      .expectOne('/api/v1/moi')
      .flush(
        { code: 'TENANT_NON_AUTORISE' },
        { status: 403, statusText: 'Forbidden' },
      );
    expect(session.surEcoleRefusee).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/choix-ecole');
  });

  it("ne change pas d'école sur un 403 d'un autre motif", () => {
    http.get('/api/v1/moi').subscribe({ error: () => undefined });
    controle
      .expectOne('/api/v1/moi')
      .flush(
        { code: 'ACCES_REFUSE' },
        { status: 403, statusText: 'Forbidden' },
      );
    expect(session.surEcoleRefusee).not.toHaveBeenCalled();
  });

  it('relance la connexion sur un 401', () => {
    http.get('/api/v1/moi').subscribe({ error: () => undefined });
    controle
      .expectOne('/api/v1/moi')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(oidc.authorize).toHaveBeenCalledTimes(1);
  });
});
