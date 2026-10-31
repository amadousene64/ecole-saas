import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  EleveListeDto,
  PageDtoEleveListeDto,
  classeLister,
  eleveRechercher,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { PageEleves } from './page-eleves';

const AWA: EleveListeDto = {
  id: 'e1',
  matricule: 'M-00001',
  prenoms: 'Awa',
  nom: 'Fictif',
  classe: '6E A',
  statut: 'INSCRIT',
  dossier: { manquantes: ['PHOTO_IDENTITE'], enAnalyse: 1, refusees: 0 },
};
const BINTOU: EleveListeDto = {
  id: 'e2',
  matricule: 'M-00002',
  prenoms: 'Bintou',
  nom: 'Fictif',
  dossier: { manquantes: [], enAnalyse: 0, refusees: 0 },
};
const COUMBA: EleveListeDto = {
  id: 'e3',
  matricule: 'M-00003',
  prenoms: 'Coumba',
  nom: 'Fictif',
};

async function creer(
  roles: string[],
  pages: PageDtoEleveListeDto[],
  classes: unknown[] = [{ id: 'k1', nom: '6E A' }],
) {
  let appel = 0;
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [eleveRechercher, () => pages[Math.min(appel++, pages.length - 1)]],
      [classeLister, classes],
    ]),
  );
  TestBed.configureTestingModule({
    providers: [...providers, provideRouter([])],
  });
  const fixture = TestBed.createComponent(PageEleves);
  await stabiliser(fixture);
  const recherches = () =>
    invoke.mock.calls
      .filter(([fn]) => fn === eleveRechercher)
      .map(([, params]) => params as Record<string, unknown>);
  return { fixture, racine: fixture.nativeElement as HTMLElement, recherches };
}

describe('PageEleves', () => {
  afterEach(() => vi.useRealTimers());

  it('affiche statut en pastille avec texte et état du dossier', async () => {
    const { racine } = await creer(
      ['SECRETARIAT'],
      [{ elements: [AWA, BINTOU, COUMBA] }],
    );
    const awa = racine.querySelector('[data-testid="eleve-M-00001"]')!;
    expect(awa.textContent).toContain('Inscrit');
    expect(awa.textContent).toContain('6E A');
    expect(awa.textContent).toContain('1 pièce manquante');
    expect(awa.textContent).toContain('1 en analyse');

    expect(
      racine.querySelector('[data-testid="eleve-M-00002"]')!.textContent,
    ).toContain('Complet');
    // Jamais inscrit, dossier non fourni (rôle sans accès aux pièces) : pas de faux « complet ».
    const coumba = racine.querySelector('[data-testid="eleve-M-00003"]')!;
    expect(coumba.textContent).toContain('Jamais inscrit');
    expect(coumba.textContent).not.toContain('Complet');
  });

  it('transmet les filtres classe et statut et repart de la première page', async () => {
    const { fixture, racine, recherches } = await creer(
      ['DIRECTEUR'],
      [{ elements: [AWA] }],
    );
    const composant = fixture.componentInstance as unknown as {
      filtrerClasse(id: string): void;
      filtrerStatut(s: string): void;
    };
    composant.filtrerClasse('k1');
    composant.filtrerStatut('PRE_INSCRIT');
    await stabiliser(fixture);

    const derniere = recherches().at(-1)!;
    expect(derniere['classe']).toBe('k1');
    expect(derniere['statut']).toBe('PRE_INSCRIT');
    expect(derniere['curseur']).toBeUndefined();
    expect(
      racine.querySelector('[data-testid="filtre-classe"]'),
    ).not.toBeNull();
  });

  it('pagine par curseur : la suite s’ajoute à la liste', async () => {
    const { fixture, racine, recherches } = await creer(
      ['SECRETARIAT'],
      [{ elements: [AWA], curseurSuivant: 'c2' }, { elements: [BINTOU] }],
    );
    expect(racine.querySelectorAll('tbody tr').length).toBe(1);

    racine
      .querySelector<HTMLButtonElement>('[data-testid="charger-suite"]')!
      .click();
    await stabiliser(fixture);

    expect(recherches().at(-1)!['curseur']).toBe('c2');
    expect(racine.querySelectorAll('tbody tr').length).toBe(2);
    expect(racine.querySelector('[data-testid="charger-suite"]')).toBeNull();
  });

  it('attend la fin de la frappe avant de chercher', async () => {
    const { fixture, recherches } = await creer(
      ['SECRETARIAT'],
      [{ elements: [AWA] }],
    );
    vi.useFakeTimers();
    const avant = recherches().length;
    const composant = fixture.componentInstance as unknown as {
      rechercher(t: string): void;
    };
    composant.rechercher('a');
    composant.rechercher('aw');
    composant.rechercher('awa');
    expect(recherches().length).toBe(avant);

    await vi.advanceTimersByTimeAsync(400);
    expect(recherches().length).toBe(avant + 1);
    expect(recherches().at(-1)!['recherche']).toBe('awa');
  });

  it('propose la création et l’import au seul personnel qui écrit', async () => {
    const ecrit = await creer(['SECRETARIAT'], [{ elements: [] }]);
    expect(
      ecrit.racine.querySelector('[data-testid="nouvel-eleve"]'),
    ).not.toBeNull();
    expect(
      ecrit.racine.querySelector('[data-testid="lien-import"]'),
    ).not.toBeNull();
    TestBed.resetTestingModule();

    const lit = await creer(['CENSEUR'], [{ elements: [] }]);
    expect(lit.racine.querySelector('[data-testid="nouvel-eleve"]')).toBeNull();
    expect(lit.racine.querySelector('[data-testid="lien-import"]')).toBeNull();
  });
});
