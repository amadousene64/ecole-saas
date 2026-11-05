import { TestBed } from '@angular/core/testing';
import {
  LienParentalDto,
  lienParentalAjouter,
  lienParentalDefinirAcces,
  lienParentalLister,
  lienParentalModifier,
  lienParentalSupprimer,
  responsableCreer,
  responsableEmettreCode,
  responsableModifier,
  responsableRechercher,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { OngletResponsables } from './onglet-responsables';

/** Responsable déjà enregistré avec ce numéro (dans une autre famille). */
const EXISTANT = {
  id: 'r9',
  prenoms: 'Awa',
  nom: 'Existante-Fictive',
  telephone: '+221770000099',
};

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
      [lienParentalSupprimer, undefined],
      [responsableCreer, { id: 'r2', nom: 'Nouveau-Fictif' }],
      [lienParentalAjouter, { id: 'l3' }],
      [responsableRechercher, [EXISTANT]],
      [responsableModifier, { ...LIEN.responsable, version: 2 }],
      [lienParentalModifier, { ...LIEN, version: 4 }],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(OngletResponsables);
  fixture.componentRef.setInput('eleveId', 'e1');
  await stabiliser(fixture);
  await stabiliser(fixture);
  const appels = (fn: unknown) => invoke.mock.calls.filter(([f]) => f === fn);
  return {
    fixture,
    racine: document.body /* les modales sont placées sous <body> */,
    appels,
  };
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

  it('affiche le code d’activation une seule fois, dans une modale détruite à la fermeture', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT']);
    cliquer(racine, 'code-Parent-Fictif');
    await stabiliser(fixture);

    expect(appels(responsableEmettreCode)).toHaveLength(1);
    const modale = racine.querySelector('dialog[data-testid="modale-code"]');
    expect(modale?.textContent).toContain('CODE-FICTIF-1234');

    cliquer(racine, 'fermer-code');
    await stabiliser(fixture);
    // Fermée : le code n'est plus nulle part dans la page.
    expect(racine.textContent).not.toContain('CODE-FICTIF-1234');
  });

  it('retire un lien après confirmation, nommée par l’action', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT']);
    cliquer(racine, 'retirer-Parent-Fictif');
    await stabiliser(fixture);

    const confirmation = racine.querySelector(
      'dialog[data-testid="confirmer-retrait"]',
    )!;
    expect(confirmation.hasAttribute('open')).toBe(true);
    expect(confirmation.getAttribute('role')).toBe('alertdialog');
    expect(confirmation.textContent).toContain(
      'Mame Parent-Fictif ne sera plus lié',
    );
    expect(appels(lienParentalSupprimer)).toHaveLength(0);

    cliquer(racine, 'confirmer-action');
    await stabiliser(fixture);
    expect(appels(lienParentalSupprimer)).toHaveLength(1);
    expect(appels(lienParentalSupprimer)[0][1]).toMatchObject({
      eleveId: 'e1',
      lienId: 'l1',
    });
    expect(confirmation.hasAttribute('open')).toBe(false);
  });

  it('annuler le retrait n’appelle pas le serveur', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT']);
    cliquer(racine, 'retirer-Parent-Fictif');
    await stabiliser(fixture);
    cliquer(racine, 'annuler-confirmation');
    await stabiliser(fixture);
    expect(appels(lienParentalSupprimer)).toHaveLength(0);
  });

  it('ajoute un responsable depuis une modale : création puis lien', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT']);
    cliquer(racine, 'ouvrir-ajout-responsable');
    await stabiliser(fixture);
    expect(
      racine.querySelector('dialog[data-testid="modale-responsable"]'),
    ).toBeTruthy();

    saisir(racine, 'resp-prenoms', 'Ndeye');
    saisir(racine, 'resp-nom', 'Nouveau-Fictif');
    saisir(racine, 'resp-telephone', '+22177');
    soumettre(racine);
    await stabiliser(fixture);

    expect(appels(responsableCreer)).toHaveLength(1);
    expect(appels(lienParentalAjouter)[0][1]).toMatchObject({
      eleveId: 'e1',
      body: { responsableId: 'r2', type: 'MERE' },
    });
    expect(
      racine.querySelector('dialog[data-testid="modale-responsable"]'),
    ).toBeNull();
    expect(racine.textContent).toContain('Responsable ajouté.');
  });

  it('propose le responsable qui a déjà ce numéro, et le lie sans le recréer', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT']);
    cliquer(racine, 'ouvrir-ajout-responsable');
    await stabiliser(fixture);

    saisir(racine, 'resp-telephone', '+221 77 000 00 99');
    await new Promise((r) => setTimeout(r, 450));
    await stabiliser(fixture);
    expect(appels(responsableRechercher)[0][1]).toMatchObject({
      telephone: '+221 77 000 00 99',
    });
    expect(
      racine.querySelector('[data-testid="doublons-telephone"]')!.textContent,
    ).toContain('Awa Existante-Fictive');

    cliquer(racine, 'lier-existant-Existante-Fictive');
    await stabiliser(fixture);
    expect(
      racine.querySelector('[data-testid="responsable-existant"]'),
    ).toBeTruthy();
    soumettre(racine);
    await stabiliser(fixture);

    expect(appels(responsableCreer)).toHaveLength(0);
    expect(appels(lienParentalAjouter)[0][1]).toMatchObject({
      body: { responsableId: 'r9' },
    });
  });

  it('modifie un responsable et son lien, chacun avec sa version', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT']);
    cliquer(racine, 'modifier-Parent-Fictif');
    await stabiliser(fixture);
    const prenoms = racine.querySelector<HTMLInputElement>(
      '[data-testid="resp-prenoms"]',
    )!;
    expect(prenoms.value).toBe('Mame');

    saisir(racine, 'resp-prenoms', 'Mame Diarra');
    soumettre(racine);
    await stabiliser(fixture);

    expect(appels(responsableModifier)[0][1]).toMatchObject({
      id: 'r1',
      body: { prenoms: 'Mame Diarra', nom: 'Parent-Fictif' },
    });
    expect(appels(lienParentalModifier)[0][1]).toMatchObject({
      lienId: 'l1',
      'If-Match': '"3"',
    });
    // Aucun doublon cherché en modification.
    expect(appels(responsableRechercher)).toHaveLength(0);
  });
});

function cliquer(racine: HTMLElement, id: string): void {
  racine.querySelector<HTMLButtonElement>(`[data-testid="${id}"]`)!.click();
}

function saisir(racine: HTMLElement, id: string, valeur: string): void {
  const champ = racine.querySelector<HTMLInputElement>(
    `[data-testid="${id}"]`,
  )!;
  champ.value = valeur;
  champ.dispatchEvent(new Event('input'));
}

/** Le bouton d'envoi est dans le pied de la modale, relié au formulaire par `form`. */
function soumettre(racine: HTMLElement): void {
  racine
    .querySelector<HTMLFormElement>('[data-testid="form-responsable"]')!
    .dispatchEvent(new Event('submit'));
}
