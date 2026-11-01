import { TestBed } from '@angular/core/testing';
import {
  InscriptionDto,
  classeLister,
  inscriptionHistorique,
  inscriptionInscrire,
  inscriptionSortir,
  inscriptionValider,
} from '@ecole/api-client';
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
  return { fixture, racine: fixture.nativeElement as HTMLElement, appels };
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
});
