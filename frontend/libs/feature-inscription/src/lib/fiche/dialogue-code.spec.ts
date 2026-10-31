import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { stabiliser } from '../test-utilitaires';
import { DialogueCode } from './dialogue-code';

async function creer() {
  TestBed.configureTestingModule({
    providers: [
      {
        provide: MAT_DIALOG_DATA,
        useValue: {
          titulaire: 'Mame Parent-Fictif',
          code: 'CODE-FICTIF-1234',
          expireLe: '2026-12-01T00:00:00Z',
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(DialogueCode);
  await stabiliser(fixture);
  return fixture.nativeElement as HTMLElement;
}

describe('DialogueCode', () => {
  afterEach(() => vi.restoreAllMocks());

  it('affiche le code, son titulaire et la mention « une seule fois »', async () => {
    const racine = await creer();
    expect(
      racine.querySelector('[data-testid="code-valeur"]')!.textContent,
    ).toContain('CODE-FICTIF-1234');
    expect(racine.textContent).toContain('Mame Parent-Fictif');
    expect(racine.textContent).toContain('ne sera plus jamais affiché');
  });

  it('copie le code dans le presse-papiers et l’annonce', async () => {
    const ecrire = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText: ecrire } });
    const racine = await creer();
    racine
      .querySelector<HTMLButtonElement>('[data-testid="copier-code"]')!
      .click();
    await Promise.resolve();
    expect(ecrire).toHaveBeenCalledWith('CODE-FICTIF-1234');
    vi.unstubAllGlobals();
  });

  it('lance l’impression du navigateur', async () => {
    const imprimer = vi.spyOn(window, 'print').mockImplementation(() => {});
    const racine = await creer();
    racine
      .querySelector<HTMLButtonElement>('[data-testid="imprimer-code"]')!
      .click();
    expect(imprimer).toHaveBeenCalledOnce();
  });
});
