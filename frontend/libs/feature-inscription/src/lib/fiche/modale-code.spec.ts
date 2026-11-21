import { TestBed } from '@angular/core/testing';
import { stabiliser } from '../test-utilitaires';
import { ModaleCode } from './modale-code';

async function creer() {
  const fixture = TestBed.createComponent(ModaleCode);
  fixture.componentRef.setInput('donnees', {
    titulaire: 'Mame Parent-Fictif',
    code: 'CODE-FICTIF-1234',
    expireLe: '2026-12-01T00:00:00Z',
  });
  const fermetures: unknown[] = [];
  fixture.componentInstance.fermee.subscribe(() => fermetures.push(true));
  await stabiliser(fixture);
  return {
    racine: document.body /* les modales sont placées sous <body> */,
    fermetures,
    fixture,
  };
}

describe('ModaleCode', () => {
  afterEach(() => vi.restoreAllMocks());

  it('affiche le code, son titulaire et la mention « une seule fois », dans une modale', async () => {
    const { racine } = await creer();
    expect(
      racine.querySelector('dialog[data-testid="modale-code"]'),
    ).toBeTruthy();
    expect(
      racine.querySelector('[data-testid="code-valeur"]')!.textContent,
    ).toContain('CODE-FICTIF-1234');
    expect(racine.textContent).toContain('Mame Parent-Fictif');
    expect(racine.textContent).toContain('ne sera plus jamais affiché');
  });

  it('copie le code dans le presse-papiers et l’annonce', async () => {
    const ecrire = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText: ecrire } });
    const { racine, fixture } = await creer();
    racine
      .querySelector<HTMLButtonElement>('[data-testid="copier-code"]')!
      .click();
    await stabiliser(fixture);
    expect(ecrire).toHaveBeenCalledWith('CODE-FICTIF-1234');
    expect(racine.textContent).toContain('Code copié');
    vi.unstubAllGlobals();
  });

  it('lance l’impression du navigateur', async () => {
    const imprimer = vi
      .spyOn(window, 'print')
      .mockImplementation(() => undefined);
    const { racine } = await creer();
    racine
      .querySelector<HTMLButtonElement>('[data-testid="imprimer-code"]')!
      .click();
    expect(imprimer).toHaveBeenCalledOnce();
  });

  it('« Fermer » ferme directement ; « × » demande confirmation tant que le code n’est pas conservé', async () => {
    const { racine, fermetures, fixture } = await creer();
    racine
      .querySelector<HTMLButtonElement>('[data-testid="fermer-modale"]')!
      .click();
    await stabiliser(fixture);
    expect(fermetures).toEqual([]);
    expect(racine.textContent).toContain("Fermer sans l'avoir copié");

    // « Revenir au code », puis le bouton « Fermer » du pied : fermeture sans question.
    racine
      .querySelector<HTMLButtonElement>('[data-testid="continuer-saisie"]')!
      .click();
    await stabiliser(fixture);
    racine
      .querySelector<HTMLButtonElement>('[data-testid="fermer-code"]')!
      .click();
    expect(fermetures).toEqual([true]);
  });
});
