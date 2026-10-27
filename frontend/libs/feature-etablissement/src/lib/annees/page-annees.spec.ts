import { TestBed } from '@angular/core/testing';
import {
  AnneeScolaireDto,
  anneeScolaireCloturer,
  anneeScolaireCreer,
  anneeScolaireLister,
  anneeScolaireOuvrir,
  calendrierLister,
  cycleLister,
  periodeLister,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { PageAnnees } from './page-annees';

const ENCOURS: AnneeScolaireDto = {
  id: 'a1',
  version: 2,
  libelle: '2026-2027',
  dateDebut: '2026-10-05',
  dateFin: '2027-07-02',
  statut: 'EN_COURS',
};
const PREPARATION: AnneeScolaireDto = {
  id: 'a2',
  version: 0,
  libelle: '2027-2028',
  dateDebut: '2027-10-04',
  dateFin: '2028-07-07',
  statut: 'PREPARATION',
};
const CLOTUREE: AnneeScolaireDto = {
  id: 'a0',
  version: 5,
  libelle: '2025-2026',
  dateDebut: '2025-10-06',
  dateFin: '2026-07-03',
  statut: 'CLOTUREE',
};

async function creer(roles: string[], annees: AnneeScolaireDto[]) {
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [anneeScolaireLister, annees],
      [anneeScolaireOuvrir, { ...PREPARATION, statut: 'EN_COURS' }],
      [anneeScolaireCloturer, { ...ENCOURS, statut: 'CLOTUREE' }],
      [
        anneeScolaireCreer,
        (p: unknown) => ({
          id: 'a3',
          version: 0,
          statut: 'PREPARATION',
          ...(p as { body: object }).body,
        }),
      ],
      [
        cycleLister,
        [{ id: 'c1', code: 'ELEMENTAIRE', libelle: 'Élémentaire' }],
      ],
      [periodeLister, []],
      [calendrierLister, []],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(PageAnnees);
  await stabiliser(fixture);
  return { fixture, invoke, racine: fixture.nativeElement as HTMLElement };
}

function bouton(racine: HTMLElement, id: string): HTMLButtonElement | null {
  return racine.querySelector<HTMLButtonElement>(`[data-testid="${id}"]`);
}

describe('PageAnnees', () => {
  it('liste les années et sélectionne par défaut celle en cours', async () => {
    const { racine } = await creer(
      ['DIRECTEUR'],
      [PREPARATION, ENCOURS, CLOTUREE],
    );

    expect(racine.querySelectorAll('[data-testid^="annee-"]')).toHaveLength(3);
    expect(
      racine.querySelector('[data-testid="detail-titre"]')?.textContent,
    ).toContain('2026-2027');
  });

  it('seul le directeur voit Ouvrir et Clôturer', async () => {
    const directeur = await creer(['DIRECTEUR'], [PREPARATION, ENCOURS]);
    expect(bouton(directeur.racine, 'ouvrir')).not.toBeNull();
    expect(bouton(directeur.racine, 'cloturer')).not.toBeNull();
    TestBed.resetTestingModule();

    const secretariat = await creer(['SECRETARIAT'], [PREPARATION, ENCOURS]);
    expect(bouton(secretariat.racine, 'ouvrir')).toBeNull();
    expect(bouton(secretariat.racine, 'cloturer')).toBeNull();
    // Il peut en revanche supprimer une année en préparation et en créer une.
    expect(bouton(secretariat.racine, 'supprimer-annee')).not.toBeNull();
    expect(
      secretariat.racine.querySelector('[data-testid="form-annee"]'),
    ).not.toBeNull();
  });

  it('le censeur ne peut rien modifier', async () => {
    const { racine } = await creer(['CENSEUR'], [PREPARATION, ENCOURS]);

    expect(bouton(racine, 'ouvrir')).toBeNull();
    expect(bouton(racine, 'cloturer')).toBeNull();
    expect(bouton(racine, 'supprimer-annee')).toBeNull();
    expect(racine.querySelector('[data-testid="form-annee"]')).toBeNull();
  });

  it("demande confirmation avant d'ouvrir, puis appelle le serveur", async () => {
    const { fixture, invoke, racine } = await creer(
      ['DIRECTEUR'],
      [PREPARATION, ENCOURS],
    );

    bouton(racine, 'ouvrir')!.click();
    await stabiliser(fixture);
    expect(
      racine.querySelector('[data-testid="confirmer"]')?.textContent,
    ).toContain('copiés pour cette année');
    expect(invoke.mock.calls.some(([fn]) => fn === anneeScolaireOuvrir)).toBe(
      false,
    );

    bouton(racine, 'confirmer-oui')!.click();
    await stabiliser(fixture);
    expect(invoke.mock.calls.some(([fn]) => fn === anneeScolaireOuvrir)).toBe(
      true,
    );
    expect(
      racine.querySelector('[data-testid="confirmation-annees"]')?.textContent,
    ).toContain('ouverte');
  });

  it("annuler la confirmation n'appelle pas le serveur", async () => {
    const { fixture, invoke, racine } = await creer(['DIRECTEUR'], [ENCOURS]);

    bouton(racine, 'cloturer')!.click();
    await stabiliser(fixture);
    expect(
      racine.querySelector('[data-testid="confirmer"]')?.textContent,
    ).toContain('définitif');
    const annuler = Array.from(
      racine.querySelectorAll<HTMLButtonElement>(
        '[data-testid="confirmer"] button',
      ),
    ).find((b) => b.textContent?.includes('Annuler'))!;
    annuler.click();
    await stabiliser(fixture);

    expect(racine.querySelector('[data-testid="confirmer"]')).toBeNull();
    expect(invoke.mock.calls.some(([fn]) => fn === anneeScolaireCloturer)).toBe(
      false,
    );
  });

  it('une année clôturée est annoncée en lecture seule', async () => {
    const { racine } = await creer(['DIRECTEUR'], [CLOTUREE]);

    expect(
      racine.querySelector('[data-testid="annee-cloturee"]'),
    ).not.toBeNull();
    expect(bouton(racine, 'ouvrir')).toBeNull();
    expect(bouton(racine, 'cloturer')).toBeNull();
    expect(bouton(racine, 'supprimer-annee')).toBeNull();
  });

  it("refuse un libellé d'année mal formé sans appeler le serveur", async () => {
    const { fixture, invoke, racine } = await creer(['DIRECTEUR'], [ENCOURS]);
    const libelle = racine.querySelector<HTMLInputElement>(
      '[data-testid="champ-annee-libelle"]',
    )!;
    libelle.value = '2027/2028';
    libelle.dispatchEvent(new Event('input'));
    bouton(racine, 'creer-annee')!.click();
    await stabiliser(fixture);

    expect(invoke.mock.calls.some(([fn]) => fn === anneeScolaireCreer)).toBe(
      false,
    );
  });
});
