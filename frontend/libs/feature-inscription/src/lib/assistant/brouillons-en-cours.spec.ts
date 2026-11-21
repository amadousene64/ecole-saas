import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  BrouillonResumeDto,
  PageDtoBrouillonResumeDto,
  brouillonInscriptionLister,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { BrouillonsEnCours } from './brouillons-en-cours';

function brouillon(n: number): BrouillonResumeDto {
  return {
    id: `b${n}`,
    libelle: `Élève fictif ${n}`,
    etape: 2,
    modifieLe: '2026-11-21T10:00:00Z',
    expireLe: '2026-12-21T10:00:00Z',
    version: 1,
  };
}

async function creer(pages: (PageDtoBrouillonResumeDto | Error)[]) {
  let appel = 0;
  const { providers, invoke } = fournisseursDeTest(
    ['SECRETARIAT'],
    new Map<unknown, unknown>([
      [
        brouillonInscriptionLister,
        () => {
          const page = pages[Math.min(appel++, pages.length - 1)];
          if (page instanceof Error) {
            throw page;
          }
          return page;
        },
      ],
    ]),
  );
  TestBed.configureTestingModule({
    providers: [...providers, provideRouter([])],
  });
  const fixture = TestBed.createComponent(BrouillonsEnCours);
  await stabiliser(fixture);
  const curseurs = () =>
    invoke.mock.calls
      .filter(([fn]) => fn === brouillonInscriptionLister)
      .map(([, params]) => (params as { curseur?: string }).curseur);
  return { fixture, racine: fixture.nativeElement as HTMLElement, curseurs };
}

describe('BrouillonsEnCours', () => {
  it('affiche la date de suppression de chaque brouillon', async () => {
    const { racine } = await creer([{ elements: [brouillon(1)] }]);

    expect(racine.textContent).toContain('Élève fictif 1');
    expect(racine.textContent).toContain(
      "supprimé le 21/12 s'il n'est pas terminé",
    );
    expect(racine.querySelector('[data-testid="brouillons-suite"]')).toBeNull();
  });

  it('charge la page suivante : aucun brouillon ne reste invisible', async () => {
    const { fixture, racine, curseurs } = await creer([
      { elements: [brouillon(1), brouillon(2)], curseurSuivant: 'c2' },
      { elements: [brouillon(3)] },
    ]);

    (
      racine.querySelector('[data-testid="brouillons-suite"]') as HTMLElement
    ).click();
    await stabiliser(fixture);

    expect(racine.querySelectorAll('li').length).toBe(3);
    expect(racine.textContent).toContain('Élève fictif 3');
    expect(curseurs()).toEqual([undefined, 'c2']);
    expect(racine.querySelector('[data-testid="brouillons-suite"]')).toBeNull();
  });

  it("montre l'erreur de chargement au lieu de se taire", async () => {
    const { racine } = await creer([new Error('réseau')]);

    expect(
      racine.querySelector(
        '[data-testid="brouillons-en-cours"] [role="alert"]',
      ),
    ).not.toBeNull();
  });
});
