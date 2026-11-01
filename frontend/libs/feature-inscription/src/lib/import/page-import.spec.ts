import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  RapportImportDto,
  importElevesImporter,
  importElevesValider,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { controlerFichierImport, PageImport } from './page-import';

const RAPPORT_OK: RapportImportDto = {
  empreinte: 'abc123',
  lignes: 2,
  erreurs: 0,
  avertissements: 1,
  importable: true,
  details: [
    { numero: 2, statut: 'OK', messages: [] },
    {
      numero: 3,
      statut: 'AVERTISSEMENT',
      messages: ['Classe presque pleine.'],
    },
  ],
};
const RAPPORT_ERREURS: RapportImportDto = {
  empreinte: 'def456',
  lignes: 2,
  erreurs: 1,
  avertissements: 0,
  importable: false,
  details: [
    { numero: 2, statut: 'OK', messages: [] },
    { numero: 3, statut: 'ERREUR', messages: ['Date de naissance invalide.'] },
  ],
};

function csv(nom = 'eleves.csv'): File {
  return new File(['prenoms;nom\nAwa;Fictif'], nom, { type: 'text/csv' });
}

async function creer(rapport: RapportImportDto, importer?: () => unknown) {
  const { providers, invoke } = fournisseursDeTest(
    ['SECRETARIAT'],
    new Map<unknown, unknown>([
      [importElevesValider, rapport],
      [
        importElevesImporter,
        importer ??
          (() => ({ elevesCrees: 1, inscriptions: 1, responsablesCrees: 0 })),
      ],
    ]),
  );
  TestBed.configureTestingModule({
    providers: [...providers, provideRouter([])],
  });
  const fixture = TestBed.createComponent(PageImport);
  await stabiliser(fixture);
  const racine = fixture.nativeElement as HTMLElement;
  const deposer = async (fichier: File) => {
    const champ = racine.querySelector<HTMLInputElement>(
      '[data-testid="champ-fichier-import"]',
    )!;
    Object.defineProperty(champ, 'files', {
      value: [fichier],
      configurable: true,
    });
    champ.dispatchEvent(new Event('change'));
    await stabiliser(fixture);
  };
  const clic = async (id: string) => {
    racine.querySelector<HTMLButtonElement>(`[data-testid="${id}"]`)!.click();
    await stabiliser(fixture);
  };
  const appels = (fn: unknown) => invoke.mock.calls.filter(([f]) => f === fn);
  return { racine, deposer, clic, appels };
}

describe('controlerFichierImport', () => {
  it('accepte CSV et XLSX, refuse le reste et les fichiers hors taille', () => {
    expect(controlerFichierImport(csv())).toBeNull();
    expect(controlerFichierImport(csv('eleves.XLSX'))).toBeNull();
    expect(controlerFichierImport(csv('eleves.pdf'))).toContain('CSV');
    expect(controlerFichierImport(new File([], 'vide.csv'))).toContain('vide');
  });
});

describe('PageImport', () => {
  it('affiche le rapport ligne par ligne avant toute écriture', async () => {
    const { racine, deposer, clic, appels } = await creer(RAPPORT_OK);
    await deposer(csv());
    await clic('verifier-import');

    expect(appels(importElevesValider)).toHaveLength(1);
    expect(appels(importElevesImporter)).toHaveLength(0);
    expect(
      racine.querySelector('[data-testid="ligne-3"]')!.textContent,
    ).toContain('Classe presque pleine.');
    expect(
      racine.querySelector('[data-testid="synthese-import"]')!.textContent,
    ).toContain('peut être importé');
  });

  it('confirme avec l’empreinte du rapport et affiche le résultat', async () => {
    const { racine, deposer, clic, appels } = await creer(RAPPORT_OK);
    await deposer(csv());
    await clic('verifier-import');
    await clic('confirmer-import');

    expect(appels(importElevesImporter)[0][1]).toMatchObject({
      empreinte: 'abc123',
    });
    expect(
      racine.querySelector('[data-testid="resultat-import"]')!.textContent,
    ).toContain('1 élève(s) créé(s)');
  });

  it('interdit la confirmation tant que le rapport contient des erreurs', async () => {
    const { racine, deposer, clic, appels } = await creer(RAPPORT_ERREURS);
    await deposer(csv());
    await clic('verifier-import');

    expect(
      racine.querySelector<HTMLButtonElement>(
        '[data-testid="confirmer-import"]',
      )!.disabled,
    ).toBe(true);
    expect(
      racine.querySelector('[data-testid="ligne-3"]')!.textContent,
    ).toContain('Date de naissance invalide.');
    expect(appels(importElevesImporter)).toHaveLength(0);
  });

  it('un 422 à la confirmation réaffiche le rapport et ne prétend rien d’importé', async () => {
    const { racine, deposer, clic } = await creer(RAPPORT_OK, () => {
      throw new HttpErrorResponse({ status: 422, error: RAPPORT_ERREURS });
    });
    await deposer(csv());
    await clic('verifier-import');
    await clic('confirmer-import');

    expect(racine.querySelector('[data-testid="resultat-import"]')).toBeNull();
    expect(
      racine.querySelector('[data-testid="erreur-import"]')!.textContent,
    ).toContain("Rien n'a été importé");
    expect(
      racine.querySelector('[data-testid="ligne-3"]')!.textContent,
    ).toContain('Date de naissance invalide.');
  });

  it('refuse côté client un mauvais format sans appeler le serveur', async () => {
    const { racine, deposer, appels } = await creer(RAPPORT_OK);
    await deposer(csv('eleves.pdf'));

    expect(
      racine.querySelector('[data-testid="erreur-import"]')!.textContent,
    ).toContain('CSV');
    expect(
      racine.querySelector<HTMLButtonElement>(
        '[data-testid="verifier-import"]',
      )!.disabled,
    ).toBe(true);
    expect(appels(importElevesValider)).toHaveLength(0);
  });
});
