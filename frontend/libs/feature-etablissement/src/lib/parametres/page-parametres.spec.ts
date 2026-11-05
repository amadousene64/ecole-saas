import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  EtablissementDto,
  etablissementLire,
  etablissementModifier,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { PageParametres } from './page-parametres';

const PROFIL: EtablissementDto = {
  id: 'e1',
  version: 3,
  nom: 'Lycée Exemple A',
  code: 'LYCEE-A',
  statut: 'PRIVE_LAIQUE',
  fuseau: 'Africa/Dakar',
  devise: 'XOF',
  ville: 'Dakar',
  phraseAccueil: 'Apprendre ensemble.',
  logoUrl: 'https://stockage.test/logo.png',
  cachetUrl: 'https://stockage.test/cachet.png',
  signatureUrl: 'https://stockage.test/signature.png',
};

async function creer(roles: string[], modifier?: (p: unknown) => unknown) {
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [etablissementLire, PROFIL],
      [
        etablissementModifier,
        modifier ??
          ((p: unknown) => ({
            ...PROFIL,
            version: 4,
            ...(p as { body: object }).body,
          })),
      ],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(PageParametres);
  await stabiliser(fixture);
  return { fixture, invoke, racine: fixture.nativeElement as HTMLElement };
}

function champ(racine: HTMLElement, id: string): HTMLInputElement {
  return racine.querySelector<HTMLInputElement>(`[data-testid="${id}"]`)!;
}

describe('PageParametres', () => {
  it('affiche le profil chargé', async () => {
    const { racine } = await creer(['DIRECTEUR']);
    expect(champ(racine, 'champ-nom').value).toBe('Lycée Exemple A');
    expect(champ(racine, 'champ-code').value).toBe('LYCEE-A');
  });

  it('affiche et envoie la ville et la phrase d’accueil de la page de connexion', async () => {
    const { fixture, invoke, racine } = await creer(['SECRETARIAT']);
    expect(champ(racine, 'champ-ville').value).toBe('Dakar');
    expect(champ(racine, 'champ-phrase-accueil').value).toBe(
      'Apprendre ensemble.',
    );

    const phrase = champ(racine, 'champ-phrase-accueil');
    phrase.value = 'Une école pour tous.';
    phrase.dispatchEvent(new Event('input'));
    racine
      .querySelector<HTMLButtonElement>('[data-testid="enregistrer"]')!
      .click();
    await stabiliser(fixture);

    const appel = invoke.mock.calls.find(
      ([fn]) => fn === etablissementModifier,
    )!;
    expect(appel[1]).toMatchObject({
      body: { ville: 'Dakar', phraseAccueil: 'Une école pour tous.' },
    });
  });

  it('le directeur enregistre avec la version lue dans If-Match', async () => {
    const { fixture, invoke, racine } = await creer(['DIRECTEUR']);
    const nom = champ(racine, 'champ-nom');
    nom.value = 'Lycée Modifié';
    nom.dispatchEvent(new Event('input'));
    racine
      .querySelector<HTMLButtonElement>('[data-testid="enregistrer"]')!
      .click();
    await stabiliser(fixture);

    const appel = invoke.mock.calls.find(
      ([fn]) => fn === etablissementModifier,
    )!;
    expect(appel[1]).toMatchObject({
      'If-Match': '"3"',
      body: { nom: 'Lycée Modifié', code: 'LYCEE-A', statut: 'PRIVE_LAIQUE' },
    });
    expect(
      racine.querySelector('[data-testid="confirmation"]')?.textContent,
    ).toContain('enregistrés');
  });

  it('affiche le message du serveur quand la modification est refusée', async () => {
    const { fixture, racine } = await creer(['SECRETARIAT'], () => {
      throw new HttpErrorResponse({
        status: 400,
        error: {
          code: 'TELEPHONE_INVALIDE',
          detail: 'Numéro de téléphone invalide.',
        },
      });
    });
    racine
      .querySelector<HTMLButtonElement>('[data-testid="enregistrer"]')!
      .click();
    await stabiliser(fixture);

    expect(
      racine.querySelector('[data-testid="erreur"]')?.textContent,
    ).toContain('Numéro de téléphone invalide.');
  });

  it("n'enregistre pas un profil sans nom", async () => {
    const { fixture, invoke, racine } = await creer(['DIRECTEUR']);
    const nom = champ(racine, 'champ-nom');
    nom.value = '';
    nom.dispatchEvent(new Event('input'));
    racine
      .querySelector<HTMLButtonElement>('[data-testid="enregistrer"]')!
      .click();
    await stabiliser(fixture);

    expect(invoke.mock.calls.some(([fn]) => fn === etablissementModifier)).toBe(
      false,
    );
  });

  it('est en lecture seule pour le censeur : champs désactivés, pas de bouton, pas de cachet', async () => {
    const { racine } = await creer(['CENSEUR']);

    expect(champ(racine, 'champ-nom').disabled).toBe(true);
    expect(racine.querySelector('[data-testid="enregistrer"]')).toBeNull();
    expect(
      racine.querySelector('[data-testid="lecture-seule"]'),
    ).not.toBeNull();
    expect(
      racine.querySelector('[data-testid="image-LOGO"] img'),
    ).not.toBeNull();
    expect(racine.querySelector('[data-testid="image-CACHET"]')).toBeNull();
    expect(racine.querySelector('[data-testid="image-SIGNATURE"]')).toBeNull();
    expect(racine.querySelector('input[type="file"]')).toBeNull();
  });

  it('le secrétariat dépose le logo mais ne voit ni le cachet ni la signature', async () => {
    const { racine } = await creer(['SECRETARIAT']);

    expect(racine.querySelector('[data-testid="fichier-LOGO"]')).not.toBeNull();
    expect(racine.querySelector('[data-testid="image-CACHET"]')).toBeNull();
    expect(racine.querySelector('[data-testid="image-SIGNATURE"]')).toBeNull();
  });

  it('le directeur voit et peut remplacer le cachet et la signature', async () => {
    const { racine } = await creer(['DIRECTEUR']);

    expect(
      racine.querySelector('[data-testid="fichier-CACHET"]'),
    ).not.toBeNull();
    expect(
      racine.querySelector('[data-testid="fichier-SIGNATURE"]'),
    ).not.toBeNull();
  });
});
