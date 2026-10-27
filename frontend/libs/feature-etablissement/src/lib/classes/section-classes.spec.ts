import { TestBed } from '@angular/core/testing';
import {
  AnneeScolaireDto,
  ClasseDto,
  anneeScolaireLister,
  classeCreer,
  classeLister,
  classeModifier,
  niveauLister,
  salleLister,
  serieLister,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { SectionClasses } from './section-classes';

const ANNEE_EN_COURS: AnneeScolaireDto = {
  id: 'a1',
  libelle: '2026-2027',
  statut: 'EN_COURS',
};
const ANNEE_PREPARATION: AnneeScolaireDto = {
  id: 'a2',
  libelle: '2027-2028',
  statut: 'PREPARATION',
};
const ANNEE_CLOTUREE: AnneeScolaireDto = {
  id: 'a0',
  libelle: '2025-2026',
  statut: 'CLOTUREE',
};

const NIVEAUX = [
  { id: 'cp', code: 'CP', libelle: 'CP', actif: true, serieIds: [] },
  {
    id: 'tle',
    code: 'TLE',
    libelle: 'Tle',
    actif: true,
    serieIds: ['s2', 's1'],
  },
];
const SERIES = [
  { id: 's1', code: 'S1', libelle: 'Série S1', actif: true },
  { id: 's2', code: 'S2', libelle: 'Série S2', actif: true },
];
const CLASSE_TS2B: ClasseDto = {
  id: 'k1',
  version: 1,
  anneeId: 'a1',
  niveauId: 'tle',
  serieId: 's2',
  nom: 'TS2B',
  capacite: 45,
};

async function creer(
  roles: string[],
  annee: AnneeScolaireDto,
  classes: ClasseDto[] = [],
) {
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [anneeScolaireLister, [annee]],
      [niveauLister, NIVEAUX],
      [serieLister, SERIES],
      [salleLister, [{ id: 'sa1', nom: 'Salle 1', capacite: 50 }]],
      [classeLister, classes],
      [
        classeCreer,
        (p: unknown) => ({
          id: 'k9',
          version: 0,
          ...(p as { body: object }).body,
        }),
      ],
      [
        classeModifier,
        (p: unknown) => ({ ...CLASSE_TS2B, ...(p as { body: object }).body }),
      ],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(SectionClasses);
  await stabiliser(fixture);
  return { fixture, invoke, racine: fixture.nativeElement as HTMLElement };
}

function f(fixture: { componentInstance: SectionClasses }) {
  return (
    fixture.componentInstance as unknown as {
      formulaire: SectionClasses['formulaire' & keyof SectionClasses];
    }
  ).formulaire as unknown as import('@angular/forms').FormGroup;
}

function choisirNiveau(
  fixture: { componentInstance: SectionClasses },
  id: string,
): void {
  f(fixture).controls['niveauId'].setValue(id);
}

describe('SectionClasses', () => {
  it("affiche les classes de l'année en cours avec niveau, série et salle", async () => {
    const { racine } = await creer(['SECRETARIAT'], ANNEE_EN_COURS, [
      CLASSE_TS2B,
    ]);
    const ligne = racine.querySelector('[data-testid="classe-TS2B"]')!;

    expect(ligne.textContent).toContain('Tle');
    expect(ligne.textContent).toContain('S2');
    expect(ligne.textContent).toContain('45');
  });

  it("n'affiche le champ série que si le niveau en propose", async () => {
    const { fixture, racine } = await creer(['SECRETARIAT'], ANNEE_EN_COURS);
    expect(
      racine.querySelector('[data-testid="champ-classe-serie"]'),
    ).toBeNull();

    choisirNiveau(fixture, 'tle');
    await stabiliser(fixture);
    expect(
      racine.querySelector('[data-testid="champ-classe-serie"]'),
    ).not.toBeNull();

    choisirNiveau(fixture, 'cp');
    await stabiliser(fixture);
    expect(
      racine.querySelector('[data-testid="champ-classe-serie"]'),
    ).toBeNull();
  });

  it('bloque la création tant que la série obligatoire manque', async () => {
    const { fixture, invoke, racine } = await creer(
      ['SECRETARIAT'],
      ANNEE_EN_COURS,
    );
    f(fixture).controls['nom'].setValue('TS1A');
    choisirNiveau(fixture, 'tle');
    await stabiliser(fixture);

    const bouton = racine.querySelector<HTMLButtonElement>(
      '[data-testid="enregistrer-classe"]',
    )!;
    expect(bouton.disabled).toBe(true);
    expect(invoke.mock.calls.some(([fn]) => fn === classeCreer)).toBe(false);

    f(fixture).controls['serieId'].setValue('s1');
    await stabiliser(fixture);
    expect(bouton.disabled).toBe(false);
  });

  it('crée une classe sans série pour un niveau sans série', async () => {
    const { fixture, invoke, racine } = await creer(
      ['DIRECTEUR'],
      ANNEE_EN_COURS,
    );
    f(fixture).controls['nom'].setValue('CPA');
    choisirNiveau(fixture, 'cp');
    await stabiliser(fixture);

    racine
      .querySelector<HTMLButtonElement>('[data-testid="enregistrer-classe"]')!
      .click();
    await stabiliser(fixture);

    const appel = invoke.mock.calls.find(([fn]) => fn === classeCreer)!;
    expect(appel[1]).toMatchObject({
      body: { anneeId: 'a1', niveauId: 'cp', nom: 'CPA', capacite: 40 },
    });
    expect(
      (appel[1] as { body: { serieId?: string } }).body.serieId,
    ).toBeUndefined();
  });

  it("fige le niveau d'une classe existante quand l'année est en cours", async () => {
    const { fixture, racine } = await creer(['SECRETARIAT'], ANNEE_EN_COURS, [
      CLASSE_TS2B,
    ]);
    racine
      .querySelector<HTMLButtonElement>('[data-testid="classe-TS2B"] button')!
      .click();
    await stabiliser(fixture);

    expect(f(fixture).controls['niveauId'].disabled).toBe(true);
    expect(racine.textContent).toContain('ne change plus une fois');
  });

  it("laisse changer le niveau d'une classe tant que l'année est en préparation", async () => {
    const { fixture, racine } = await creer(
      ['SECRETARIAT'],
      ANNEE_PREPARATION,
      [{ ...CLASSE_TS2B, anneeId: 'a2' }],
    );
    racine
      .querySelector<HTMLButtonElement>('[data-testid="classe-TS2B"] button')!
      .click();
    await stabiliser(fixture);

    expect(f(fixture).controls['niveauId'].disabled).toBe(false);
  });

  it('une année clôturée est en lecture seule', async () => {
    const { racine } = await creer(['DIRECTEUR'], ANNEE_CLOTUREE, [
      CLASSE_TS2B,
    ]);

    expect(
      racine.querySelector('[data-testid="classes-cloturee"]'),
    ).not.toBeNull();
    expect(racine.querySelector('[data-testid="form-classe"]')).toBeNull();
    expect(
      racine.querySelector('[data-testid="classe-TS2B"] button'),
    ).toBeNull();
  });

  it('le censeur lit les classes sans pouvoir les modifier', async () => {
    const { racine } = await creer(['CENSEUR'], ANNEE_EN_COURS, [CLASSE_TS2B]);

    expect(racine.querySelector('[data-testid="classe-TS2B"]')).not.toBeNull();
    expect(racine.querySelector('[data-testid="form-classe"]')).toBeNull();
    expect(
      racine.querySelector('[data-testid="classe-TS2B"] button'),
    ).toBeNull();
  });
});
