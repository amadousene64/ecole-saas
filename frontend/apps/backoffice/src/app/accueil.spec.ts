import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Api, statistiqueDirection } from '@ecole/api-client';
import { CONFIG_APPS, Session, URLS_DEV } from '@ecole/auth';
import { AccueilBackoffice } from './accueil';

describe('AccueilBackoffice', () => {
  const TABLEAU_VIDE = {
    anneeId: 'a1',
    anneeLibelle: '2026-2027',
    inscrits: 0,
    parCycle: [],
    parNiveau: [],
    parStatut: [],
    dossiers: { total: 0, dont: 0, pourcentage: 0 },
    familles: { total: 0, dont: 0, pourcentage: 0 },
    courbe: [],
  };

  async function creer(roles: string[]) {
    const invoke = vi.fn(async () => TABLEAU_VIDE);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: Session,
          useValue: {
            ecoleActive: signal({ id: 'a', code: 'A', nom: 'Lycée Exemple A' }),
            utilisateur: signal('utilisateur.test'),
            roles: signal(roles),
            moi: signal(null),
          },
        },
        { provide: Api, useValue: { invoke } },
        {
          provide: CONFIG_APPS,
          useValue: { courante: 'backoffice', urls: URLS_DEV },
        },
      ],
    });
    const fixture = TestBed.createComponent(AccueilBackoffice);
    await fixture.whenStable();
    fixture.detectChanges();
    if (roles.includes('DIRECTEUR')) {
      // Le tableau de bord arrive par un import() dynamique, hors de la file d'Angular.
      await vi.waitFor(() => {
        fixture.detectChanges();
        expect(
          fixture.nativeElement.querySelector('ecole-tableau-de-bord'),
        ).not.toBeNull();
      });
    }
    await fixture.whenStable();
    fixture.detectChanges();
    return { racine: fixture.nativeElement as HTMLElement, invoke };
  }

  it('montre le tableau de bord au directeur, avec son profil en dessous', async () => {
    const { racine, invoke } = await creer(['DIRECTEUR']);

    expect(racine.querySelector('ecole-tableau-de-bord')).not.toBeNull();
    expect(racine.querySelector('ecole-page-accueil')).toBeNull();
    expect(racine.querySelector('[data-testid="moi"]')).not.toBeNull();
    expect(invoke).toHaveBeenCalledWith(
      statistiqueDirection,
      expect.objectContaining({ periode: 'ANNEE' }),
    );
  });

  it.each(['SECRETARIAT', 'CENSEUR', 'COMPTABLE'])(
    'garde l’accueil simple pour %s, sans demander de statistiques',
    async (role) => {
      const { racine, invoke } = await creer([role]);

      expect(racine.querySelector('ecole-page-accueil')).not.toBeNull();
      expect(racine.querySelector('ecole-tableau-de-bord')).toBeNull();
      expect(invoke).not.toHaveBeenCalledWith(
        statistiqueDirection,
        expect.anything(),
      );
    },
  );
});
