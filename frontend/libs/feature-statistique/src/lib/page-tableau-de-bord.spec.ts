import { Directive, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TableauDeBordDto } from '@ecole/api-client';
import { NgxEchartsDirective } from 'ngx-echarts';
import { PageTableauDeBord } from './page-tableau-de-bord';
import { ServiceStatistiques } from './service-statistiques';

/** Remplace la directive ECharts : pas de canvas sous jsdom, on ne teste que la page. */
// Même sélecteur que la vraie directive (ngx-echarts), qui n'a pas notre préfixe.
// eslint-disable-next-line @angular-eslint/directive-selector
@Directive({ selector: '[echarts]' })
class EchartsFactice {
  readonly options = input<unknown>();
  readonly autoResize = input<boolean>();
}

const TABLEAU: TableauDeBordDto = {
  anneeId: 'a1',
  anneeLibelle: '2026-2027',
  calculeLe: '2026-11-04T10:00:00Z',
  periode: 'ANNEE',
  inscrits: 1234,
  parCycle: [
    { cycle: 'ELEMENTAIRE', libelle: 'Primaire', nombre: 20 },
    { cycle: 'MOYEN', libelle: 'Collège', nombre: 30 },
    { cycle: 'SECONDAIRE', libelle: 'Lycée', nombre: 10 },
  ],
  parNiveau: [
    {
      niveauId: 'n1',
      code: 'CP',
      libelle: 'CP',
      cycle: 'ELEMENTAIRE',
      nombre: 20,
    },
    { niveauId: 'n2', code: '6E', libelle: '6e', cycle: 'MOYEN', nombre: 30 },
  ],
  parStatut: [
    { statut: 'PRE_INSCRIT', nombre: 3 },
    { statut: 'INSCRIT', nombre: 60 },
    { statut: 'TRANSFERE', nombre: 0 },
    { statut: 'RADIE', nombre: 0 },
    { statut: 'ABANDON', nombre: 0 },
  ],
  dossiers: { total: 63, dont: 42, pourcentage: 67 },
  familles: { total: 50, dont: 20, pourcentage: 40 },
  courbe: [
    { lundi: '2026-11-02', nombre: 4 },
    { lundi: '2026-11-09', nombre: 9 },
  ],
};

const VIDE: TableauDeBordDto = {
  ...TABLEAU,
  inscrits: 0,
  parStatut: TABLEAU.parStatut!.map((s) => ({ ...s, nombre: 0 })),
  dossiers: { total: 0, dont: 0, pourcentage: 0 },
  familles: { total: 0, dont: 0, pourcentage: 0 },
};

async function creer(
  direction: ReturnType<typeof vi.fn> = vi.fn(async () => TABLEAU),
) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ServiceStatistiques, useValue: { direction } },
    ],
  });
  TestBed.overrideComponent(PageTableauDeBord, {
    remove: { imports: [NgxEchartsDirective], providers: [] },
    add: { imports: [EchartsFactice] },
  });
  const fixture = TestBed.createComponent(PageTableauDeBord);
  await stabiliser(fixture);
  return { fixture, direction, racine: fixture.nativeElement as HTMLElement };
}

async function stabiliser(fixture: {
  whenStable(): Promise<unknown>;
  detectChanges(): void;
}) {
  await fixture.whenStable();
  fixture.detectChanges();
  await fixture.whenStable();
}

const el = (racine: HTMLElement, id: string) =>
  racine.querySelector<HTMLElement>(`[data-testid="${id}"]`);

describe('PageTableauDeBord', () => {
  it('affiche les chiffres clés, l’année et le détail par cycle', async () => {
    const { racine } = await creer();

    expect(racine.querySelector('h1')?.textContent).toContain(
      'Tableau de bord',
    );
    expect(racine.textContent).toContain('Année 2026-2027');
    expect(el(racine, 'kpi-inscrits')?.textContent).toMatch(/1\s?234/);
    expect(el(racine, 'kpi-inscrits')?.textContent).toContain('Collège 30');
    expect(el(racine, 'kpi-dossiers')?.textContent).toContain('67 %');
    expect(el(racine, 'kpi-dossiers')?.textContent).toContain(
      '42 sur 63 élèves',
    );
    expect(el(racine, 'kpi-familles')?.textContent).toContain('40 %');
    expect(
      el(racine, 'kpi-familles')!
        .querySelector('progress')
        ?.getAttribute('aria-label'),
    ).toBe('Part des familles connectées');
  });

  it('charge l’année entière tous cycles au départ, puis suit les filtres', async () => {
    const { fixture, direction, racine } = await creer();
    expect(direction).toHaveBeenLastCalledWith('ANNEE', undefined);

    el(racine, 'periode-JOURS_30')!.querySelector('button')!.click();
    await stabiliser(fixture);
    expect(direction).toHaveBeenLastCalledWith('JOURS_30', undefined);

    el(racine, 'cycle-MOYEN')!.querySelector('button')!.click();
    await stabiliser(fixture);
    expect(direction).toHaveBeenLastCalledWith('JOURS_30', 'MOYEN');

    el(racine, 'cycle-TOUS')!.querySelector('button')!.click();
    await stabiliser(fixture);
    expect(direction).toHaveBeenLastCalledWith('JOURS_30', undefined);
  });

  it('donne à chaque graphique un résumé texte et un tableau « Voir les données »', async () => {
    const { fixture, racine } = await creer();

    const zones = racine.querySelectorAll('[role="img"]');
    expect(zones).toHaveLength(3);
    expect(zones[0].getAttribute('aria-label')).toContain('élèves inscrits');

    expect(el(racine, 'donnees-0')).toBeNull();
    const bouton = el(racine, 'voir-donnees-0') as HTMLButtonElement;
    expect(bouton.textContent).toContain('Voir les données');
    expect(bouton.getAttribute('aria-expanded')).toBe('false');
    bouton.click();
    await stabiliser(fixture);

    expect(bouton.getAttribute('aria-expanded')).toBe('true');
    expect(bouton.textContent).toContain('Masquer les données');
    const lignes = Array.from(
      el(racine, 'donnees-0')!.querySelectorAll('tbody tr'),
    ).map((tr) => tr.textContent?.replace(/\s+/g, ' ').trim());
    expect(lignes).toEqual(['CP (Primaire)20', '6e (Collège)30']);
    // Les en-têtes sont des colonnes déclarées, pour les lecteurs d'écran.
    expect(
      el(racine, 'donnees-0')!.querySelector('th[scope="col"]'),
    ).not.toBeNull();

    bouton.click();
    await stabiliser(fixture);
    expect(el(racine, 'donnees-0')).toBeNull();
  });

  it('propose d’inscrire un élève quand il n’y a encore aucune inscription', async () => {
    const { racine } = await creer(vi.fn(async () => VIDE));

    expect(el(racine, 'etat-vide')?.textContent).toContain(
      "Aucune inscription pour l'instant",
    );
    expect(racine.querySelector('[role="img"]')).toBeNull();
    expect(racine.querySelector('a[href="/eleves/nouveau"]')).not.toBeNull();
  });

  it('renvoie vers les années quand aucune n’est en cours', async () => {
    const { racine } = await creer(
      vi.fn(async () => ({
        ...VIDE,
        anneeId: undefined,
        anneeLibelle: undefined,
      })),
    );

    expect(el(racine, 'etat-vide')?.textContent).toContain(
      'Aucune année scolaire en cours',
    );
    expect(
      racine.querySelector('a[href="/etablissement/annees"]'),
    ).not.toBeNull();
  });

  it('annonce une erreur et permet de réessayer', async () => {
    const direction = vi
      .fn()
      .mockRejectedValueOnce(new Error('coupé'))
      .mockResolvedValue(TABLEAU);
    const { fixture, racine } = await creer(direction);

    expect(el(racine, 'erreur')?.getAttribute('role')).toBe('alert');
    expect(el(racine, 'erreur')?.textContent).toContain(
      'momentanément indisponibles',
    );

    Array.from(el(racine, 'erreur')!.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Réessayer'))!
      .click();
    await stabiliser(fixture);

    expect(el(racine, 'erreur')).toBeNull();
    expect(el(racine, 'kpi-inscrits')).not.toBeNull();
  });

  it('ignore une réponse arrivée après une demande plus récente', async () => {
    let resoudreLente!: (t: TableauDeBordDto) => void;
    const lente = new Promise<TableauDeBordDto>((r) => (resoudreLente = r));
    const direction = vi
      .fn()
      .mockReturnValueOnce(lente)
      .mockResolvedValue({ ...TABLEAU, inscrits: 7 });
    const { fixture, racine } = await creer(direction);

    el(racine, 'cycle-MOYEN')!.querySelector('button')!.click();
    await stabiliser(fixture);
    resoudreLente({ ...TABLEAU, inscrits: 999 });
    await stabiliser(fixture);

    expect(el(racine, 'kpi-inscrits')?.textContent).toContain('7');
    expect(el(racine, 'kpi-inscrits')?.textContent).not.toContain('999');
  });
});
