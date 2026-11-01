import { TableauDeBordDto } from '@ecole/api-client';
import {
  COULEURS_CYCLES,
  formaterLundi,
  graphiqueCourbe,
  graphiqueNiveaux,
  graphiqueStatuts,
} from './graphiques';

const TABLEAU: TableauDeBordDto = {
  anneeId: 'a1',
  anneeLibelle: '2026-2027',
  inscrits: 60,
  parCycle: [],
  parNiveau: [
    {
      niveauId: 'n1',
      code: 'CP',
      libelle: 'CP',
      cycle: 'ELEMENTAIRE',
      nombre: 20,
    },
    { niveauId: 'n2', code: '6E', libelle: '6e', cycle: 'MOYEN', nombre: 30 },
    {
      niveauId: 'n3',
      code: 'TLE',
      libelle: 'Tle',
      cycle: 'SECONDAIRE',
      nombre: 10,
    },
    {
      niveauId: 'n4',
      code: 'CE1',
      libelle: 'CE1',
      cycle: 'ELEMENTAIRE',
      nombre: 0,
    },
  ],
  parStatut: [
    { statut: 'PRE_INSCRIT', nombre: 3 },
    { statut: 'INSCRIT', nombre: 60 },
    { statut: 'TRANSFERE', nombre: 0 },
    { statut: 'RADIE', nombre: 2 },
    { statut: 'ABANDON', nombre: 1 },
  ],
  courbe: [
    { lundi: '2026-11-02', nombre: 4 },
    { lundi: '2026-11-09', nombre: 9 },
    { lundi: '2026-11-16', nombre: 0 },
  ],
};

describe('graphiqueNiveaux', () => {
  it('colore chaque niveau selon son cycle et garde les niveaux vides', () => {
    const g = graphiqueNiveaux(TABLEAU);
    const serie = (
      g.options['series'] as {
        data: { value: number; itemStyle: { color: string } }[];
      }[]
    )[0];

    expect(serie.data.map((d) => d.value)).toEqual([20, 30, 10, 0]);
    expect(serie.data.map((d) => d.itemStyle.color)).toEqual([
      COULEURS_CYCLES.ELEMENTAIRE,
      COULEURS_CYCLES.MOYEN,
      COULEURS_CYCLES.SECONDAIRE,
      COULEURS_CYCLES.ELEMENTAIRE,
    ]);
    expect(g.lignes[1]).toEqual({
      libelle: '6e',
      valeur: 30,
      detail: 'Collège',
    });
  });

  it("résume en texte pour un lecteur d'écran, sans niveau vide", () => {
    const resume = graphiqueNiveaux(TABLEAU).resume;
    expect(resume).toContain('60 élèves inscrits');
    expect(resume).toContain('CP 20');
    expect(resume).toContain('6e 30');
    expect(resume).not.toContain('CE1');
  });

  it("dit qu'il n'y a personne plutôt que de lister des zéros", () => {
    const vide = graphiqueNiveaux({ ...TABLEAU, inscrits: 0, parNiveau: [] });
    expect(vide.resume).toBe('Aucun élève inscrit pour le moment.');
  });
});

describe('graphiqueStatuts', () => {
  it('liste tous les statuts avec leur libellé, zéros compris', () => {
    const g = graphiqueStatuts(TABLEAU);
    expect(g.lignes).toEqual([
      { libelle: 'Pré-inscrits', valeur: 3 },
      { libelle: 'Inscrits', valeur: 60 },
      { libelle: 'Transférés', valeur: 0 },
      { libelle: 'Radiés', valeur: 2 },
      { libelle: 'Abandons', valeur: 1 },
    ]);
    expect(g.resume).toContain('Inscrits 60');
  });
});

describe('graphiqueCourbe', () => {
  it('trace une valeur par semaine et nomme la semaine la plus active', () => {
    const g = graphiqueCourbe(TABLEAU);
    const serie = (g.options['series'] as { data: number[] }[])[0];

    expect(serie.data).toEqual([4, 9, 0]);
    expect(g.lignes[1].libelle).toBe('Semaine du 9 novembre 2026');
    expect(g.resume).toContain('13 inscriptions sur 3 semaines');
    expect(g.resume).toContain('semaine du 9 novembre 2026 avec 9');
  });

  it("n'invente pas de pic quand il n'y a aucune inscription", () => {
    const g = graphiqueCourbe({
      ...TABLEAU,
      courbe: [{ lundi: '2026-11-02', nombre: 0 }],
    });
    expect(g.resume).toBe('Aucune inscription sur la période.');
  });

  it('les options désactivent le texte généré par ECharts (le résumé de la page fait foi)', () => {
    const aria = graphiqueCourbe(TABLEAU).options['aria'] as {
      label: { enabled: boolean };
      decal: { show: boolean };
    };
    expect(aria.label.enabled).toBe(false);
    expect(aria.decal.show).toBe(true);
  });
});

describe('formaterLundi', () => {
  it('formate sans décalage de fuseau', () => {
    expect(formaterLundi('2026-11-02')).toBe('2 novembre 2026');
    expect(formaterLundi('2026-01-05', true)).toBe('5 janv.');
    expect(formaterLundi(undefined)).toBe('');
  });
});
