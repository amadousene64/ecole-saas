import { TestBed } from '@angular/core/testing';
import {
  CreneauDto,
  MatiereDto,
  creneauCopier,
  creneauLister,
  cycleLister,
  matiereAjouterModele,
  matiereCreer,
  matiereLister,
} from '@ecole/api-client';
import { CHAMPS_FORMULAIRE } from '@ecole/ui';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { heure, libelleJours, ModaleCreneau } from './modale-creneau';
import { ModaleMatiere } from './modale-matiere';
import { SectionGrille } from './section-grille';
import { SectionMatieres } from './section-matieres';

const MATHS: MatiereDto = {
  id: 'm1',
  version: 0,
  code: 'MATHS',
  libelle: 'Mathématiques',
  cycles: ['ELEMENTAIRE', 'MOYEN', 'SECONDAIRE'],
  ordre: 20,
  actif: true,
};
const ESP: MatiereDto = {
  id: 'm2',
  version: 0,
  code: 'ESP',
  libelle: 'Espagnol',
  groupe: 'Langues vivantes 2',
  cycles: ['MOYEN', 'SECONDAIRE'],
  ordre: 130,
  actif: false,
};
const CYCLES = [
  { id: 'c1', code: 'ELEMENTAIRE', libelle: 'Élémentaire' },
  { id: 'c2', code: 'MOYEN', libelle: 'Moyen' },
];
const MATIN: CreneauDto = {
  id: 'k1',
  version: 0,
  cycleId: 'c2',
  debut: '08:00:00',
  fin: '10:00:00',
  jours: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  type: 'COURS',
};

function configurer(roles: string[], reponses: Map<unknown, unknown>) {
  const { providers, invoke } = fournisseursDeTest(roles, reponses);
  TestBed.configureTestingModule({
    providers: [...providers, CHAMPS_FORMULAIRE],
  });
  return invoke;
}

function cliquer(racine: HTMLElement, testId: string): void {
  (racine.querySelector(`[data-testid="${testId}"]`) as HTMLElement).click();
}

describe('libellés des créneaux', () => {
  it('résume les jours consécutifs et abrège les heures', () => {
    expect(libelleJours(MATIN.jours)).toBe('du lundi au vendredi');
    expect(libelleJours(['MONDAY', 'WEDNESDAY', 'SATURDAY'])).toBe(
      'lundi, mercredi, samedi',
    );
    expect(libelleJours(['SATURDAY'])).toBe('samedi');
    expect(heure('08:00:00')).toBe('08:00');
  });
});

describe('SectionMatieres', () => {
  it('liste les matières avec leurs cycles et signale une matière inactive', async () => {
    configurer(['CENSEUR'], new Map([[matiereLister, [MATHS, ESP]]]));
    const fixture = TestBed.createComponent(SectionMatieres);
    await stabiliser(fixture);
    const racine = fixture.nativeElement as HTMLElement;

    const maths = racine.querySelector('[data-testid="matiere-MATHS"]')!;
    expect(maths.textContent).toContain('Élémentaire, Moyen, Secondaire');
    const esp = racine.querySelector('[data-testid="matiere-ESP"]')!;
    expect(esp.textContent).toContain('Langues vivantes 2');
    expect(esp.textContent).toContain('Inactive');
    // Le censeur organise les enseignements : il a les boutons.
    expect(
      racine.querySelector('[data-testid="ajouter-matiere"]'),
    ).not.toBeNull();
  });

  it('« Ajouter les matières usuelles » dit combien ont été ajoutées', async () => {
    configurer(
      ['SECRETARIAT'],
      new Map<unknown, unknown>([
        [matiereLister, [MATHS]],
        [matiereAjouterModele, [ESP]],
      ]),
    );
    const fixture = TestBed.createComponent(SectionMatieres);
    await stabiliser(fixture);
    const racine = fixture.nativeElement as HTMLElement;

    cliquer(racine, 'matieres-usuelles');
    await stabiliser(fixture);

    expect(
      racine.querySelector('[data-testid="info-matieres"]')?.textContent,
    ).toContain('1 matière ajoutée');
  });
});

describe('ModaleMatiere', () => {
  it('exige un cycle et envoie les cycles dans l’ordre du référentiel', async () => {
    const invoke = configurer(
      ['DIRECTEUR'],
      new Map<unknown, unknown>([
        [
          matiereCreer,
          (p: unknown) => ({ id: 'm9', ...(p as { body: object }).body }),
        ],
      ]),
    );
    const fixture = TestBed.createComponent(ModaleMatiere);
    await stabiliser(fixture);
    const modale = fixture.componentInstance as unknown as {
      formulaire: { setValue(v: object): void };
      enregistrer(): Promise<void>;
      basculer(code: string, coche: boolean): void;
    };
    modale.formulaire.setValue({
      code: 'SVT',
      libelle: 'SVT',
      groupe: '',
      ordre: 90,
      actif: true,
    });

    await modale.enregistrer();
    await stabiliser(fixture);
    expect(invoke).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain(
      'Choisissez au moins un cycle.',
    );

    modale.basculer('SECONDAIRE', true);
    modale.basculer('MOYEN', true);
    await modale.enregistrer();
    expect(invoke).toHaveBeenCalledWith(matiereCreer, {
      body: expect.objectContaining({
        code: 'SVT',
        cycles: ['MOYEN', 'SECONDAIRE'],
        groupe: undefined,
      }),
    });
  });
});

describe('ModaleCreneau', () => {
  it('propose la semaine du lundi au vendredi par défaut', async () => {
    configurer(['DIRECTEUR'], new Map());
    const fixture = TestBed.createComponent(ModaleCreneau);
    fixture.componentRef.setInput('cycleId', 'c2');
    fixture.componentRef.setInput('libelleCycle', 'Moyen');
    await stabiliser(fixture);
    const coches = Array.from(
      document.body.querySelectorAll('[data-testid^="jour-"] input:checked'),
    );
    expect(coches).toHaveLength(5);
  });
});

describe('SectionGrille', () => {
  it('affiche la grille du premier cycle et copie celle d’un autre dans une grille vide', async () => {
    const invoke = configurer(
      ['DIRECTEUR'],
      new Map<unknown, unknown>([
        [cycleLister, CYCLES],
        [
          creneauLister,
          (p: unknown) =>
            (p as { cycleId: string }).cycleId === 'c2' ? [MATIN] : [],
        ],
        [creneauCopier, [{ ...MATIN, id: 'k2', cycleId: 'c1' }]],
      ]),
    );
    const fixture = TestBed.createComponent(SectionGrille);
    await stabiliser(fixture);
    const racine = fixture.nativeElement as HTMLElement;
    const section = fixture.componentInstance as unknown as {
      source: { set(v: string): void };
      choisirCycle(id: string): Promise<void>;
    };

    // Premier cycle (Élémentaire) : grille vide, copie proposée.
    expect(racine.textContent).toContain('Aucun créneau pour ce cycle.');
    section.source.set('c2');
    await stabiliser(fixture);
    cliquer(racine, 'copier-grille');
    await stabiliser(fixture);

    expect(invoke).toHaveBeenCalledWith(creneauCopier, {
      cycleId: 'c1',
      body: { depuisCycleId: 'c2' },
    });
    const ligne = racine.querySelector('[data-testid="creneau-08:00"]')!;
    expect(ligne.textContent).toContain('08:00 – 10:00');
    expect(ligne.textContent).toContain('du lundi au vendredi');
    expect(racine.querySelector('[data-testid="copier-grille"]')).toBeNull();

    await section.choisirCycle('c2');
    await stabiliser(fixture);
    expect(
      racine.querySelector('[data-testid="creneau-08:00"]'),
    ).not.toBeNull();
  });
});
