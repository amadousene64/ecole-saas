import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  AnneeScolaireDto,
  anneeScolaireCreer,
  anneeScolaireLister,
  calendrierLister,
  cycleLister,
  periodeLister,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { PagePreparationAnnee } from './page-preparation-annee';

const PREPARATION: AnneeScolaireDto = {
  id: 'a2',
  version: 0,
  libelle: '2027-2028',
  dateDebut: '2027-10-04',
  dateFin: '2028-07-07',
  statut: 'PREPARATION',
};

async function creer(roles: string[]) {
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [anneeScolaireLister, [PREPARATION]],
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
  TestBed.configureTestingModule({
    // Après la création, l'adresse devient …/preparation/:id : une route doit l'accepter.
    providers: [...providers, provideRouter([{ path: '**', children: [] }])],
  });
  const fixture = TestBed.createComponent(PagePreparationAnnee);
  await stabiliser(fixture);
  return { fixture, invoke, racine: fixture.nativeElement as HTMLElement };
}

function saisir(racine: HTMLElement, id: string, valeur: string): void {
  const champ = racine.querySelector<HTMLInputElement>(
    `[data-testid="${id}"]`,
  )!;
  champ.value = valeur;
  champ.dispatchEvent(new Event('input'));
}

function cliquer(racine: HTMLElement, id: string): void {
  racine.querySelector<HTMLElement>(`[data-testid="${id}"]`)!.click();
}

function titreEtape(racine: HTMLElement): string {
  return (
    racine.querySelector('[data-testid="titre-etape"]')?.textContent?.trim() ??
    ''
  );
}

describe('PagePreparationAnnee', () => {
  it("refuse un libellé d'année mal formé sans appeler le serveur", async () => {
    const { fixture, invoke, racine } = await creer(['DIRECTEUR']);
    saisir(racine, 'champ-annee-libelle', '2027/2028');
    saisir(racine, 'champ-annee-debut', '2027-10-04');
    saisir(racine, 'champ-annee-fin', '2028-07-07');
    cliquer(racine, 'etape-suivante');
    await stabiliser(fixture);

    expect(invoke.mock.calls.some(([fn]) => fn === anneeScolaireCreer)).toBe(
      false,
    );
    expect(titreEtape(racine)).toBe("L'année scolaire");
  });

  it("crée l'année en préparation puis passe aux périodes", async () => {
    const { fixture, invoke, racine } = await creer(['SECRETARIAT']);
    saisir(racine, 'champ-annee-libelle', '2027-2028');
    saisir(racine, 'champ-annee-debut', '2027-10-04');
    saisir(racine, 'champ-annee-fin', '2028-07-07');
    cliquer(racine, 'etape-suivante');
    await stabiliser(fixture);

    const appel = invoke.mock.calls.find(([fn]) => fn === anneeScolaireCreer);
    expect((appel?.[1] as { body: object }).body).toEqual({
      libelle: '2027-2028',
      dateDebut: '2027-10-04',
      dateFin: '2028-07-07',
    });
    expect(titreEtape(racine)).toBe('Périodes');
  });

  it("à la dernière étape, seul le directeur peut ouvrir l'année", async () => {
    for (const [roles, ouvrir] of [
      [['DIRECTEUR'], true],
      [['SECRETARIAT'], false],
    ] as const) {
      const { fixture, racine } = await creer([...roles]);
      saisir(racine, 'champ-annee-libelle', '2027-2028');
      saisir(racine, 'champ-annee-debut', '2027-10-04');
      saisir(racine, 'champ-annee-fin', '2028-07-07');
      for (let i = 0; i < 3; i++) {
        cliquer(racine, 'etape-suivante');
        await stabiliser(fixture);
      }
      expect(titreEtape(racine)).toBe("Ouverture de l'année");
      expect(
        racine.querySelector('[data-testid="ouvrir-annee"]') !== null,
      ).toBe(ouvrir);
      TestBed.resetTestingModule();
    }
  });
});
