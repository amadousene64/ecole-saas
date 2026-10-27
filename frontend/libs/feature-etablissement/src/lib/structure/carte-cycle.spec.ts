import { TestBed } from '@angular/core/testing';
import { CycleDto, cycleModifier } from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { CarteCycle } from './carte-cycle';

const MOYEN: CycleDto = {
  id: 'c2',
  version: 1,
  code: 'MOYEN',
  libelle: 'Moyen',
  typePeriode: 'SEMESTRE',
  modeNotation: 'COEFFICIENT',
  baseMoyenne: 20,
};

async function creer(modifiable: boolean) {
  const { providers, invoke } = fournisseursDeTest(
    modifiable ? ['DIRECTEUR'] : ['CENSEUR'],
    new Map<unknown, unknown>([
      [
        cycleModifier,
        (p: unknown) => ({
          ...MOYEN,
          version: 2,
          ...(p as { body: object }).body,
        }),
      ],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(CarteCycle);
  fixture.componentRef.setInput('cycle', MOYEN);
  fixture.componentRef.setInput('modifiable', modifiable);
  await stabiliser(fixture);
  return { fixture, invoke, racine: fixture.nativeElement as HTMLElement };
}

describe('CarteCycle', () => {
  it('enregistre les paramètres avec la version lue', async () => {
    const { fixture, invoke, racine } = await creer(true);

    racine
      .querySelector<HTMLButtonElement>('[data-testid="enregistrer-cycle"]')!
      .click();
    await stabiliser(fixture);

    const appel = invoke.mock.calls.find(([fn]) => fn === cycleModifier)!;
    expect(appel[1]).toMatchObject({
      id: 'c2',
      'If-Match': '"1"',
      body: {
        libelle: 'Moyen',
        typePeriode: 'SEMESTRE',
        modeNotation: 'COEFFICIENT',
        baseMoyenne: 20,
      },
    });
  });

  it("n'offre pas l'enregistrement hors du directeur", async () => {
    const { racine } = await creer(false);
    expect(
      racine.querySelector('[data-testid="enregistrer-cycle"]'),
    ).toBeNull();
  });

  it('refuse la base 10 avec des coefficients, avant même le serveur', async () => {
    const { fixture, invoke, racine } = await creer(true);
    fixture.componentInstance['formulaire'].controls.baseMoyenne.setValue(10);
    await stabiliser(fixture);

    expect(
      racine.querySelector('[data-testid="base-incompatible"]'),
    ).not.toBeNull();
    const bouton = racine.querySelector<HTMLButtonElement>(
      '[data-testid="enregistrer-cycle"]',
    )!;
    expect(bouton.disabled).toBe(true);
    bouton.click();
    await stabiliser(fixture);
    expect(invoke.mock.calls.some(([fn]) => fn === cycleModifier)).toBe(false);
  });

  it('accepte la base 10 en notation par barème', async () => {
    const { fixture, racine } = await creer(true);
    const f = fixture.componentInstance['formulaire'];
    f.controls.modeNotation.setValue('BAREME');
    f.controls.baseMoyenne.setValue(10);
    await stabiliser(fixture);

    expect(
      racine.querySelector('[data-testid="base-incompatible"]'),
    ).toBeNull();
  });
});
