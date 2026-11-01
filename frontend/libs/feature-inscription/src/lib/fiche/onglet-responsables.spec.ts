import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import {
  LienParentalDto,
  lienParentalDefinirAcces,
  lienParentalLister,
  responsableEmettreCode,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { DialogueCode } from './dialogue-code';
import { OngletResponsables } from './onglet-responsables';

const LIEN: LienParentalDto = {
  id: 'l1',
  version: 3,
  type: 'MERE',
  responsableLegal: true,
  payeur: true,
  accesEspaceParent: true,
  responsable: {
    id: 'r1',
    prenoms: 'Mame',
    nom: 'Parent-Fictif',
    telephone: '+221770000001',
    compteActive: true,
  },
};
const BLOQUE: LienParentalDto = { ...LIEN, id: 'l2', accesEspaceParent: false };

async function creer(roles: string[], liens: LienParentalDto[] = [LIEN]) {
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [lienParentalLister, liens],
      [
        lienParentalDefinirAcces,
        (p: unknown) => ({
          ...LIEN,
          version: 4,
          accesEspaceParent: (p as { body: { acces: boolean } }).body.acces,
        }),
      ],
      [
        responsableEmettreCode,
        { code: 'CODE-FICTIF-1234', expireLe: '2026-12-01T00:00:00Z' },
      ],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(OngletResponsables);
  fixture.componentRef.setInput('eleveId', 'e1');
  await stabiliser(fixture);
  await stabiliser(fixture);
  const appels = (fn: unknown) => invoke.mock.calls.filter(([f]) => f === fn);
  return { fixture, racine: fixture.nativeElement as HTMLElement, appels };
}

describe('OngletResponsables', () => {
  it('l’accès à l’espace parent n’est modifiable que par le directeur', async () => {
    const directeur = await creer(['DIRECTEUR']);
    const interrupteur = (r: HTMLElement) =>
      r.querySelector<HTMLElement>(
        '[data-testid="acces-Parent-Fictif"] button',
      )!;
    expect(interrupteur(directeur.racine).hasAttribute('disabled')).toBe(false);
    TestBed.resetTestingModule();

    const secretariat = await creer(['SECRETARIAT']);
    expect(interrupteur(secretariat.racine).hasAttribute('disabled')).toBe(
      true,
    );
    expect(secretariat.racine.textContent).toContain('Réservé au directeur');
  });

  it('envoie la décision du directeur avec la version lue', async () => {
    const { fixture, racine, appels } = await creer(['DIRECTEUR']);
    racine
      .querySelector<HTMLElement>('[data-testid="acces-Parent-Fictif"] button')!
      .click();
    await stabiliser(fixture);

    const envoi = appels(lienParentalDefinirAcces);
    expect(envoi).toHaveLength(1);
    expect(envoi[0][1]).toMatchObject({
      eleveId: 'e1',
      lienId: 'l1',
      'If-Match': '"3"',
      body: { acces: false },
    });
    // La mention de blocage apparaît, avec son effet.
    expect(
      racine.querySelector('[data-testid="acces-bloque"]')!.textContent,
    ).toContain('ne voit plus cet élève');
  });

  it('signale en clair un accès bloqué', async () => {
    const { racine } = await creer(['SECRETARIAT'], [BLOQUE]);
    const mention = racine.querySelector('[data-testid="acces-bloque"]')!;
    expect(mention.textContent).toContain('Accès bloqué');
  });

  it('n’affiche aucune mention de blocage pour un accès autorisé', async () => {
    const { racine } = await creer(['DIRECTEUR']);
    expect(racine.querySelector('[data-testid="acces-bloque"]')).toBeNull();
  });

  it('ouvre le code d’activation une seule fois, dans un dialogue', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT']);
    const dialogue = TestBed.inject(MatDialog);
    const ouvrir = vi.spyOn(dialogue, 'open');

    racine
      .querySelector<HTMLButtonElement>('[data-testid="code-Parent-Fictif"]')!
      .click();
    await stabiliser(fixture);

    expect(appels(responsableEmettreCode)).toHaveLength(1);
    expect(ouvrir).toHaveBeenCalledTimes(1);
    const [composant, config] = ouvrir.mock.calls[0];
    expect(composant).toBe(DialogueCode);
    expect(config?.data).toMatchObject({ code: 'CODE-FICTIF-1234' });
    // Le code n'est nulle part dans la page elle-même.
    expect(racine.textContent).not.toContain('CODE-FICTIF-1234');
  });
});
