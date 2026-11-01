import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiConfiguration } from '@ecole/api-client';
import { ServiceEleves } from './service-eleves';

describe('ServiceEleves : aucun nom dans les URL', () => {
  let http: HttpTestingController;
  let service: ServiceEleves;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ApiConfiguration, useValue: { rootUrl: '' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ServiceEleves);
  });

  afterEach(() => http.verify());

  it('envoie le nom cherché et le curseur dans le corps d’un POST, jamais dans l’URL', async () => {
    const promesse = service.rechercher({
      recherche: 'Awa Fictif',
      classe: 'k1',
      statut: 'INSCRIT',
      curseur: 'c2',
      taille: 25,
    });
    const requete = http.expectOne((r) => r.url.startsWith('/api/v1/eleves'));
    requete.flush({ elements: [] });
    await promesse;

    expect(requete.request.method).toBe('POST');
    expect(requete.request.url).toBe('/api/v1/eleves/recherche');
    expect(requete.request.urlWithParams).toBe('/api/v1/eleves/recherche');
    expect(requete.request.params.keys()).toEqual([]);
    expect(requete.request.body).toEqual({
      recherche: 'Awa Fictif',
      classe: 'k1',
      statut: 'INSCRIT',
      curseur: 'c2',
      taille: 25,
    });
  });
});
