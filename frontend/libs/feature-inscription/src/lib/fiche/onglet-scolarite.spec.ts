import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  InscriptionDto,
  classeLister,
  inscriptionHistorique,
  inscriptionInscrire,
  inscriptionSortir,
  inscriptionValider,
} from '@ecole/api-client';
import { Api } from '@ecole/api-client';
import { Session } from '@ecole/auth';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { OngletScolarite } from './onglet-scolarite';

const PRE: InscriptionDto = {
  id: 'i1',
  version: 5,
  classe: '6E A',
  classeId: 'k1',
  statut: 'PRE_INSCRIT',
  dateEntree: '2026-10-01',
};

async function creer(roles: string[], historique: InscriptionDto[]) {
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [inscriptionHistorique, historique],
      [classeLister, [{ id: 'k1', nom: '6E A' }]],
      [
        inscriptionInscrire,
        {
          ...PRE,
          statut: 'INSCRIT',
          avertissements: [
            'Classe 6E A : effectif au-dessus de la capacité (31/30).',
          ],
        },
      ],
      [inscriptionValider, { ...PRE, statut: 'INSCRIT' }],
      [inscriptionSortir, { ...PRE, statut: 'TRANSFERE' }],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(OngletScolarite);
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

const clic = (racine: HTMLElement, id: string) =>
  racine.querySelector<HTMLButtonElement>(`[data-testid="${id}"]`)!.click();

describe('OngletScolarite', () => {
  it('propose d’inscrire un élève sans inscription active, et rien au personnel en lecture', async () => {
    const ecrit = await creer(['SECRETARIAT'], []);
    expect(
      ecrit.racine.querySelector('[data-testid="action-inscrire"]'),
    ).not.toBeNull();
    expect(
      ecrit.racine.querySelector('[data-testid="action-sortie"]'),
    ).toBeNull();
    TestBed.resetTestingModule();

    const lit = await creer(['CENSEUR'], []);
    expect(
      lit.racine.querySelector('[data-testid="action-inscrire"]'),
    ).toBeNull();
  });

  it('affiche les avertissements de capacité renvoyés par le serveur', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT'], []);
    clic(racine, 'action-inscrire');
    await stabiliser(fixture);
    const formulaire = (
      fixture.componentInstance as unknown as {
        formulaire: import('@angular/forms').FormGroup;
      }
    ).formulaire;
    formulaire.controls['classeId'].setValue('k1');
    clic(racine, 'confirmer-inscription');
    await stabiliser(fixture);

    expect(appels(inscriptionInscrire)[0][1]).toMatchObject({
      body: { eleveId: 'e1', classeId: 'k1', statut: 'INSCRIT' },
    });
    expect(
      racine.querySelector('[data-testid="avertissements"]')!.textContent,
    ).toContain('au-dessus de la capacité');
  });

  it('valide une pré-inscription avec la version lue', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT'], [PRE]);
    clic(racine, 'action-valider');
    await stabiliser(fixture);
    expect(appels(inscriptionValider)[0][1]).toMatchObject({
      id: 'i1',
      'If-Match': '"5"',
    });
  });

  it('réserve la radiation au directeur', async () => {
    const options = async (role: string): Promise<string> => {
      const { fixture, racine } = await creer([role], [PRE]);
      clic(racine, 'action-sortie');
      await stabiliser(fixture);
      const champ = racine.querySelector<HTMLElement>(
        '[data-testid="champ-sortie"]',
      )!;
      champ.click();
      await stabiliser(fixture);
      return document.body.textContent ?? '';
    };
    const secretariat = await options('SECRETARIAT');
    expect(secretariat).toContain('Transféré');
    expect(secretariat).not.toContain('Radié');
    TestBed.resetTestingModule();

    const directeur = await options('DIRECTEUR');
    expect(directeur).toContain('Radié');
  });

  it('radiation : la modale du statut se ferme, puis une confirmation exige le motif', async () => {
    const { fixture, racine, appels } = await creer(['DIRECTEUR'], [PRE]);
    clic(racine, 'action-sortie');
    await stabiliser(fixture);
    const ecran = fixture.componentInstance as unknown as {
      sortie: import('@angular/forms').FormGroup;
    };
    ecran.sortie.setValue({ statut: 'RADIE', date: '2026-11-05', motif: '' });
    racine
      .querySelector<HTMLFormElement>('[data-testid="form-sortie"]')!
      .dispatchEvent(new Event('submit'));
    await stabiliser(fixture);
    await stabiliser(fixture);

    // Jamais deux modales : celle du statut est détruite, la confirmation est ouverte.
    const confirmation = racine.querySelector(
      'dialog[data-testid="confirmer-radiation"]',
    )!;
    expect(
      racine.querySelector('dialog[data-testid="modale-sortie"]'),
    ).toBeNull();
    expect(confirmation.hasAttribute('open')).toBe(true);
    expect(confirmation.textContent).toContain('5 novembre 2026');
    expect(appels(inscriptionSortir)).toHaveLength(0);

    // Sans motif : refusé, rien n'est envoyé.
    clic(racine, 'confirmer-action');
    await stabiliser(fixture);
    expect(confirmation.textContent).toContain('Indiquez le motif.');
    expect(appels(inscriptionSortir)).toHaveLength(0);

    const motif = racine.querySelector<HTMLTextAreaElement>(
      '[data-testid="motif-confirmation"]',
    )!;
    motif.value = 'Exclusion définitive (conseil de discipline fictif)';
    motif.dispatchEvent(new Event('input'));
    clic(racine, 'confirmer-action');
    await stabiliser(fixture);

    expect(appels(inscriptionSortir)[0][1]).toMatchObject({
      body: {
        statut: 'RADIE',
        date: '2026-11-05',
        motif: 'Exclusion définitive (conseil de discipline fictif)',
      },
    });
    // Fermée puis détruite : plus rien de la confirmation (ni le motif saisi) dans la page.
    expect(confirmation.isConnected).toBe(false);
  });

  describe('chargement des classes', () => {
    /** Écran monté avec une session et un « serveur » dont on garde la main. */
    async function monter(roles: string[], classes: () => unknown) {
      const rolesSession = signal(roles);
      const invoke = vi.fn(async (fn: unknown) => {
        if (fn === inscriptionHistorique) {
          return [];
        }
        if (fn === classeLister) {
          return classes();
        }
        throw new Error('appel inattendu');
      });
      TestBed.configureTestingModule({
        providers: [
          { provide: Api, useValue: { invoke } },
          { provide: Session, useValue: { roles: rolesSession } },
        ],
      });
      const fixture = TestBed.createComponent(OngletScolarite);
      fixture.componentRef.setInput('eleveId', 'e1');
      await stabiliser(fixture);
      await stabiliser(fixture);
      const racine = document.body; /* les modales sont placées sous <body> */
      const nombre = () =>
        invoke.mock.calls.filter(([f]) => f === classeLister).length;
      return { fixture, racine, rolesSession, nombre };
    }

    it('charge les classes quand le droit d’écrire arrive après la création de l’écran', async () => {
      const { fixture, racine, rolesSession, nombre } = await monter([], () => [
        { id: 'k1', nom: '6E A' },
      ]);
      expect(nombre()).toBe(0);

      rolesSession.set(['SECRETARIAT']);
      await stabiliser(fixture);
      await stabiliser(fixture);
      expect(nombre()).toBe(1);

      clic(racine, 'action-inscrire');
      await stabiliser(fixture);
      racine
        .querySelector<HTMLElement>('[data-testid="champ-classe"]')!
        .click();
      await stabiliser(fixture);
      expect(document.body.textContent).toContain('6E A');
    });

    it('dit pourquoi la liste est vide quand les classes ne se chargent pas, et permet de réessayer', async () => {
      let echec = true;
      const { fixture, racine, nombre } = await monter(['SECRETARIAT'], () => {
        if (echec) {
          throw new Error('coupé');
        }
        return [{ id: 'k1', nom: '6E A' }];
      });
      expect(nombre()).toBe(1);

      clic(racine, 'action-inscrire');
      await stabiliser(fixture);
      // Rouvrir le formulaire retente le chargement ; il échoue encore : le message reste affiché.
      expect(nombre()).toBe(2);
      const alerte = racine.querySelector('[data-testid="erreur-classes"]');
      expect(alerte?.getAttribute('role')).toBe('alert');
      expect(alerte?.textContent).toContain(
        "Les classes n'ont pas pu être chargées",
      );

      echec = false;
      clic(racine, 'reessayer-classes');
      await stabiliser(fixture);
      expect(racine.querySelector('[data-testid="erreur-classes"]')).toBeNull();
      racine
        .querySelector<HTMLElement>('[data-testid="champ-classe"]')!
        .click();
      await stabiliser(fixture);
      expect(document.body.textContent).toContain('6E A');
    });

    it('ne recharge pas les classes déjà chargées', async () => {
      const { fixture, racine, nombre } = await monter(['SECRETARIAT'], () => [
        { id: 'k1', nom: '6E A' },
      ]);
      clic(racine, 'action-inscrire');
      await stabiliser(fixture);
      expect(nombre()).toBe(1);
    });
  });
});
