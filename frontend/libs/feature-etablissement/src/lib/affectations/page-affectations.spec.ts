import { TestBed } from '@angular/core/testing';
import {
  AffectationDto,
  affectationAffectables,
  affectationLister,
  affectationTerminer,
  anneeScolaireLister,
  classeLister,
  cycleLister,
  matiereLister,
  niveauLister,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import {
  libelleAffectation,
  nomMembre,
} from '../services/service-affectations';
import { PageAffectations } from './page-affectations';

const EN_COURS: AffectationDto = {
  id: 'f1',
  version: 0,
  utilisateurId: 'u1',
  type: 'ENSEIGNEMENT',
  classeId: 'k1',
  classe: '6E A',
  matiere: 'Mathématiques',
  debut: '2026-10-05',
  enCours: true,
};
const TERMINEE: AffectationDto = {
  id: 'f2',
  version: 1,
  utilisateurId: 'u2',
  type: 'SURVEILLANCE',
  codeCycle: 'MOYEN',
  debut: '2026-10-05',
  fin: '2026-11-01',
  enCours: false,
};

async function creer() {
  const { providers, invoke } = fournisseursDeTest(
    ['CENSEUR'],
    new Map<unknown, unknown>([
      [
        anneeScolaireLister,
        [
          {
            id: 'a1',
            libelle: '2026-2027',
            statut: 'EN_COURS',
            dateDebut: '2026-10-05',
          },
        ],
      ],
      [
        affectationAffectables,
        [
          {
            utilisateurId: 'u1',
            prenoms: 'Moussa',
            nom: 'Fictif',
            roles: ['ENSEIGNANT'],
          },
        ],
      ],
      [matiereLister, []],
      [niveauLister, [{ id: 'n6', cycleId: 'c2' }]],
      [cycleLister, [{ id: 'c2', code: 'MOYEN' }]],
      [affectationLister, [EN_COURS, TERMINEE]],
      [classeLister, [{ id: 'k1', nom: '6E A', niveauId: 'n6' }]],
      [affectationTerminer, undefined],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(PageAffectations);
  await stabiliser(fixture);
  return { fixture, invoke, racine: fixture.nativeElement as HTMLElement };
}

describe('libellés des affectations', () => {
  it('nomme la personne, ou son compte tant qu’il ne s’est pas connecté', () => {
    expect(
      nomMembre({ utilisateurId: 'u1', prenoms: 'Moussa', nom: 'Fictif' }),
    ).toBe('FICTIF Moussa');
    expect(nomMembre(undefined, '0000-abcd')).toBe(
      'Compte jamais connecté · …abcd',
    );
    expect(libelleAffectation(EN_COURS)).toBe('Mathématiques · 6E A');
    expect(libelleAffectation(TERMINEE)).toBe('Surveillance · Moyen');
    expect(libelleAffectation({ type: 'TITULAIRE', classe: 'CI A' })).toBe(
      'Titulaire · CI A',
    );
  });
});

describe('PageAffectations', () => {
  it('montre les affectations en cours ; l’historique à la demande', async () => {
    const { fixture, racine } = await creer();
    const table = () =>
      racine.querySelector('[data-testid="table-affectations"]')!.textContent ??
      '';
    expect(table()).toContain('FICTIF Moussa');
    expect(table()).toContain('Mathématiques · 6E A');
    expect(table()).not.toContain('Surveillance · Moyen');

    (
      racine.querySelector(
        '[data-testid="afficher-historique"] input',
      ) as HTMLInputElement
    ).click();
    await stabiliser(fixture);
    expect(table()).toContain('Surveillance · Moyen');
    expect(table()).toContain('Terminée');
  });

  it('termine une affectation après confirmation, avec la version lue', async () => {
    const { fixture, invoke, racine } = await creer();
    (
      racine.querySelector(
        '[data-testid="terminer-Mathématiques · 6E A"]',
      ) as HTMLElement
    ).click();
    await stabiliser(fixture);
    (
      document.body.querySelector(
        '[data-testid="confirmer-action"]',
      ) as HTMLElement
    ).click();
    await stabiliser(fixture);
    expect(invoke).toHaveBeenCalledWith(affectationTerminer, {
      id: 'f1',
      'If-Match': '"0"',
      body: {},
    });
  });
});
