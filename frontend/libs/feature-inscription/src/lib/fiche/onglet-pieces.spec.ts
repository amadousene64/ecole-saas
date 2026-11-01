import { TestBed } from '@angular/core/testing';
import {
  PieceDto,
  pieceDeposer,
  pieceLister,
  pieceUrl,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import {
  controlerFichier,
  DELAIS_SONDAGE,
  OngletPieces,
  TAILLE_PIECE_MAX,
} from './onglet-pieces';

const ACTE_EN_ANALYSE: PieceDto = {
  id: 'p1',
  type: 'ACTE_NAISSANCE',
  statut: 'EN_ANALYSE',
  creeLe: '2026-10-31T10:00:00Z',
};
const ACTE_SAIN: PieceDto = { ...ACTE_EN_ANALYSE, statut: 'SAIN' };
const ACTE_REFUSE: PieceDto = { ...ACTE_EN_ANALYSE, statut: 'INFECTE' };
const MEDICAL_SAIN: PieceDto = {
  id: 'p2',
  type: 'CERTIFICAT_MEDICAL',
  statut: 'SAIN',
  creeLe: '2026-10-31T10:00:00Z',
};

/** `listes` : ce que renvoie successivement le serveur à chaque relecture. */
async function creer(
  roles: string[],
  listes: PieceDto[][],
  minuteries = false,
) {
  let lecture = 0;
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [pieceLister, () => listes[Math.min(lecture++, listes.length - 1)]],
      [pieceUrl, { url: 'https://stockage.exemple/p1?signature=x' }],
      [pieceDeposer, { id: 'p9', statut: 'EN_ANALYSE' }],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  if (minuteries) {
    vi.useFakeTimers();
  }
  const fixture = TestBed.createComponent(OngletPieces);
  fixture.componentRef.setInput('eleveId', 'e1');
  fixture.detectChanges();
  if (minuteries) {
    await vi.advanceTimersByTimeAsync(0);
  } else {
    await stabiliser(fixture);
  }
  fixture.detectChanges();
  const appels = (fn: unknown) => invoke.mock.calls.filter(([f]) => f === fn);
  return { fixture, racine: fixture.nativeElement as HTMLElement, appels };
}

describe('controlerFichier', () => {
  const fichier = (taille: number, type: string) =>
    new File([new Uint8Array(taille)], 'x', { type });

  it('accepte PDF, JPEG et PNG jusqu’à la taille maximale', () => {
    for (const type of ['application/pdf', 'image/jpeg', 'image/png']) {
      expect(controlerFichier(fichier(1000, type))).toBeNull();
    }
    expect(
      controlerFichier(fichier(TAILLE_PIECE_MAX, 'application/pdf')),
    ).toBeNull();
  });

  it('refuse un fichier vide, trop gros ou d’un autre format', () => {
    expect(controlerFichier(fichier(0, 'application/pdf'))).toContain('vide');
    expect(
      controlerFichier(fichier(TAILLE_PIECE_MAX + 1, 'application/pdf')),
    ).toContain('volumineux');
    expect(controlerFichier(fichier(10, 'text/html'))).toContain('Formats');
  });
});

describe('OngletPieces', () => {
  afterEach(() => vi.useRealTimers());

  it('rafraîchit tant que la pièce est en analyse puis s’arrête à « vérifiée »', async () => {
    const { fixture, racine, appels } = await creer(
      ['SECRETARIAT'],
      [[ACTE_EN_ANALYSE], [ACTE_EN_ANALYSE], [ACTE_SAIN]],
      true,
    );
    expect(racine.textContent).toContain('Analyse en cours');
    expect(appels(pieceLister)).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(DELAIS_SONDAGE[0]);
    fixture.detectChanges();
    expect(appels(pieceLister)).toHaveLength(2);
    expect(racine.textContent).toContain('Analyse en cours');

    await vi.advanceTimersByTimeAsync(DELAIS_SONDAGE[1]);
    fixture.detectChanges();
    expect(appels(pieceLister)).toHaveLength(3);
    expect(racine.textContent).toContain('Vérifiée');

    // Plus rien en analyse : plus aucune relecture.
    await vi.advanceTimersByTimeAsync(60_000);
    expect(appels(pieceLister)).toHaveLength(3);
  });

  it('s’arrête aussi à « refusée » et bloque l’ouverture', async () => {
    const { fixture, racine, appels } = await creer(
      ['SECRETARIAT'],
      [[ACTE_EN_ANALYSE], [ACTE_REFUSE]],
      true,
    );
    await vi.advanceTimersByTimeAsync(DELAIS_SONDAGE[0]);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(60_000);

    expect(appels(pieceLister)).toHaveLength(2);
    expect(racine.textContent).toContain('Refusée');
    expect(
      racine.querySelector('[data-testid="ouvrir-ACTE_NAISSANCE"]'),
    ).toBeNull();
  });

  it('n’interroge plus le serveur une fois l’écran fermé', async () => {
    const { fixture, appels } = await creer(
      ['SECRETARIAT'],
      [[ACTE_EN_ANALYSE]],
      true,
    );
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(appels(pieceLister)).toHaveLength(1);
  });

  it('n’ouvre qu’une pièce vérifiée, par une URL signée dans un nouvel onglet', async () => {
    const { fixture, racine, appels } = await creer(
      ['SECRETARIAT'],
      [[ACTE_EN_ANALYSE, { ...ACTE_SAIN, id: 'p3', type: 'PHOTO_IDENTITE' }]],
    );
    expect(
      racine.querySelector('[data-testid="ouvrir-ACTE_NAISSANCE"]'),
    ).toBeNull();
    const ouverture = vi.spyOn(window, 'open').mockReturnValue(null);

    racine
      .querySelector<HTMLButtonElement>(
        '[data-testid="ouvrir-PHOTO_IDENTITE"]',
      )!
      .click();
    await stabiliser(fixture);

    expect(appels(pieceUrl)).toHaveLength(1);
    expect(ouverture).toHaveBeenCalledWith(
      'https://stockage.exemple/p1?signature=x',
      '_blank',
      'noopener,noreferrer',
    );
    ouverture.mockRestore();
  });

  it('le certificat médical est réservé : le secrétariat ne peut pas l’ouvrir, le censeur oui', async () => {
    const secretariat = await creer(['SECRETARIAT'], [[MEDICAL_SAIN]]);
    expect(
      secretariat.racine.querySelector(
        '[data-testid="ouvrir-CERTIFICAT_MEDICAL"]',
      ),
    ).toBeNull();
    expect(secretariat.racine.textContent).toContain('Réservé');
    TestBed.resetTestingModule();

    const censeur = await creer(['CENSEUR'], [[MEDICAL_SAIN]]);
    expect(
      censeur.racine.querySelector('[data-testid="ouvrir-CERTIFICAT_MEDICAL"]'),
    ).not.toBeNull();
    // Le censeur ne dépose ni ne supprime.
    expect(
      censeur.racine.querySelector('[data-testid="deposer-piece"]'),
    ).toBeNull();
    expect(
      censeur.racine.querySelector(
        '[data-testid="supprimer-CERTIFICAT_MEDICAL"]',
      ),
    ).toBeNull();
  });

  it('refuse côté client un fichier trop gros sans appeler le serveur', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT'], [[]]);
    const champ = racine.querySelector<HTMLInputElement>(
      '[data-testid="champ-fichier-piece"]',
    )!;
    const gros = new File([new Uint8Array(TAILLE_PIECE_MAX + 1)], 'acte.pdf', {
      type: 'application/pdf',
    });
    Object.defineProperty(champ, 'files', {
      value: [gros],
      configurable: true,
    });
    champ.dispatchEvent(new Event('change'));
    await stabiliser(fixture);

    expect(
      racine.querySelector('[data-testid="erreur-pieces"]')!.textContent,
    ).toContain('volumineux');
    expect(
      racine.querySelector<HTMLButtonElement>('[data-testid="deposer-piece"]')!
        .disabled,
    ).toBe(true);
    expect(appels(pieceDeposer)).toHaveLength(0);
  });
});
