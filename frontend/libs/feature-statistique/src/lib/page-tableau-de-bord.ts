import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { RouterLink } from '@angular/router';
import { TableauDeBordDto } from '@ecole/api-client';
import { EnTetePage, EtatVide, Squelette } from '@ecole/ui';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { chargerEcharts } from './echarts';
import {
  COULEURS_CYCLES,
  Graphique,
  graphiqueCourbe,
  graphiqueNiveaux,
  graphiqueStatuts,
  LIBELLES_CYCLES,
} from './graphiques';
import {
  Cycle,
  messageErreur,
  Periode,
  ServiceStatistiques,
} from './service-statistiques';

type Etat = 'chargement' | 'pret' | 'erreur';

const PERIODES: readonly { valeur: Periode; libelle: string }[] = [
  { valeur: 'JOURS_30', libelle: '30 jours' },
  { valeur: 'JOURS_90', libelle: '90 jours' },
  { valeur: 'ANNEE', libelle: 'Année' },
];

const CYCLES: readonly { valeur: Cycle | 'TOUS'; libelle: string }[] = [
  { valeur: 'TOUS', libelle: 'Tous les cycles' },
  { valeur: 'ELEMENTAIRE', libelle: LIBELLES_CYCLES.ELEMENTAIRE },
  { valeur: 'MOYEN', libelle: LIBELLES_CYCLES.MOYEN },
  { valeur: 'SECONDAIRE', libelle: LIBELLES_CYCLES.SECONDAIRE },
];

/**
 * Tableau de bord de la direction (accueil du directeur) : effectifs, état des inscriptions,
 * dossiers et familles connectées, courbe des inscriptions. Que des nombres. Chaque graphique a
 * une alternative accessible (résumé lu à la place du dessin, et « Voir les données » : le même
 * contenu en tableau). ECharts n'est chargé qu'avec cette page.
 */
@Component({
  selector: 'ecole-tableau-de-bord',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    EnTetePage,
    EtatVide,
    MatButtonModule,
    MatButtonToggleModule,
    NgxEchartsDirective,
    RouterLink,
    Squelette,
  ],
  providers: [provideEchartsCore({ echarts: chargerEcharts })],
  template: `
    <ecole-en-tete-page titre="Tableau de bord" [sousTitre]="sousTitre()">
      <button
        actions
        mat-stroked-button
        type="button"
        data-testid="actualiser"
        [disabled]="etat() === 'chargement'"
        (click)="charger()"
      >
        Actualiser
      </button>
    </ecole-en-tete-page>

    <div class="filtres" data-testid="filtres">
      <mat-button-toggle-group
        aria-label="Période de la courbe des inscriptions"
        data-testid="filtre-periode"
        hideSingleSelectionIndicator
        [value]="periode()"
        (change)="periode.set($event.value)"
      >
        @for (p of periodes; track p.valeur) {
          <mat-button-toggle
            [value]="p.valeur"
            [attr.data-testid]="'periode-' + p.valeur"
            >{{ p.libelle }}</mat-button-toggle
          >
        }
      </mat-button-toggle-group>
      <mat-button-toggle-group
        aria-label="Cycle"
        data-testid="filtre-cycle"
        hideSingleSelectionIndicator
        [value]="cycle()"
        (change)="cycle.set($event.value)"
      >
        @for (c of cycles; track c.valeur) {
          <mat-button-toggle
            [value]="c.valeur"
            [attr.data-testid]="'cycle-' + c.valeur"
            >{{ c.libelle }}</mat-button-toggle
          >
        }
      </mat-button-toggle-group>
    </div>

    @if (etat() === 'erreur') {
      <section class="carte erreur" role="alert" data-testid="erreur">
        <p>{{ erreur() }}</p>
        <button mat-stroked-button type="button" (click)="charger()">
          Réessayer
        </button>
      </section>
    } @else if (!tableau()) {
      <ecole-squelette variante="cartes" [lignes]="3" />
    } @else if (!tableau()!.anneeId) {
      <ecole-etat-vide
        titre="Aucune année scolaire en cours"
        detail="Les statistiques apparaissent dès qu'une année est ouverte."
      >
        <a mat-flat-button routerLink="/etablissement/annees"
          >Années et périodes</a
        >
      </ecole-etat-vide>
    } @else {
      <section
        class="kpis"
        aria-label="Chiffres clés"
        [attr.aria-busy]="etat() === 'chargement'"
      >
        <article class="carte kpi" data-testid="kpi-inscrits">
          <h2>Inscrits</h2>
          <p class="valeur">
            {{ entier(tableau()!.inscrits ?? 0) }}
          </p>
          <ul class="cycles" aria-label="Inscrits par cycle">
            @for (c of tableau()!.parCycle ?? []; track c.cycle) {
              <li>
                <span
                  class="point-cycle"
                  aria-hidden="true"
                  [style.background]="couleurCycle(c.cycle)"
                ></span>
                {{ c.libelle }} <strong>{{ c.nombre ?? 0 }}</strong>
              </li>
            }
          </ul>
        </article>

        <article class="carte kpi" data-testid="kpi-dossiers">
          <h2>Dossiers complets</h2>
          <p class="valeur">{{ tableau()!.dossiers?.pourcentage ?? 0 }} %</p>
          <progress
            max="100"
            aria-label="Part des dossiers complets"
            [value]="tableau()!.dossiers?.pourcentage ?? 0"
          ></progress>
          <p class="detail texte-secondaire">
            {{ tableau()!.dossiers?.dont ?? 0 }} sur
            {{ tableau()!.dossiers?.total ?? 0 }} élèves (acte de naissance et
            photo déposés)
          </p>
        </article>

        <article class="carte kpi" data-testid="kpi-familles">
          <h2>Familles connectées</h2>
          <p class="valeur">{{ tableau()!.familles?.pourcentage ?? 0 }} %</p>
          <progress
            max="100"
            aria-label="Part des familles connectées"
            [value]="tableau()!.familles?.pourcentage ?? 0"
          ></progress>
          <p class="detail texte-secondaire">
            {{ tableau()!.familles?.dont ?? 0 }} sur
            {{ tableau()!.familles?.total ?? 0 }} familles ont activé leur
            compte
          </p>
        </article>
      </section>

      @if (vide()) {
        <ecole-etat-vide
          titre="Aucune inscription pour l'instant"
          detail="Les graphiques se remplissent dès les premières inscriptions."
        >
          <a mat-flat-button routerLink="/eleves/nouveau">Inscrire un élève</a>
        </ecole-etat-vide>
      } @else {
        <div class="graphiques">
          @for (g of graphiques(); track g.titre; let i = $index) {
            <section
              class="carte graphique"
              [class.large]="i === 2"
              [attr.aria-labelledby]="'titre-graphique-' + i"
              [attr.data-testid]="'graphique-' + i"
            >
              <header>
                <h2 [id]="'titre-graphique-' + i">{{ g.titre }}</h2>
                <button
                  type="button"
                  class="voir-donnees"
                  [attr.aria-expanded]="donneesOuvertes().has(i)"
                  [attr.aria-controls]="'donnees-graphique-' + i"
                  [attr.data-testid]="'voir-donnees-' + i"
                  (click)="basculerDonnees(i)"
                >
                  {{
                    donneesOuvertes().has(i)
                      ? 'Masquer les données'
                      : 'Voir les données'
                  }}
                </button>
              </header>
              <div
                class="zone"
                role="img"
                echarts
                [options]="g.options"
                [autoResize]="true"
                [attr.aria-label]="g.resume"
              ></div>
              <div [id]="'donnees-graphique-' + i">
                @if (donneesOuvertes().has(i)) {
                  <div class="defilement">
                    <table [attr.data-testid]="'donnees-' + i">
                      <caption class="sr-only">
                        {{
                          g.titre
                        }}
                      </caption>
                      <thead>
                        <tr>
                          <th scope="col">
                            {{
                              i === 2
                                ? 'Semaine'
                                : i === 0
                                  ? 'Niveau'
                                  : 'Statut'
                            }}
                          </th>
                          <th scope="col" class="num">{{ g.colonne }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (l of g.lignes; track $index) {
                          <tr>
                            <th scope="row">
                              {{ l.libelle }}
                              @if (l.detail) {
                                <span class="texte-secondaire">
                                  ({{ l.detail }})</span
                                >
                              }
                            </th>
                            <td class="num">{{ l.valeur }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
              </div>
            </section>
          }
        </div>
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .filtres {
      display: flex;
      flex-wrap: wrap;
      gap: 12px 16px;
      margin-bottom: 16px;
    }
    .kpis {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    }
    .kpi {
      padding: 16px;
    }
    .kpi h2,
    .graphique h2 {
      margin: 0;
      font-size: var(--ecole-libelle-taille, 13px);
      line-height: var(--ecole-libelle-interligne, 18px);
      font-weight: 600;
      color: var(--ecole-texte-secondaire);
    }
    .graphique h2 {
      font-size: 16px;
      line-height: 24px;
      color: var(--ecole-texte);
    }
    .valeur {
      margin: 4px 0 8px;
      font-size: 32px;
      line-height: 40px;
      font-weight: 700;
    }
    .detail {
      margin: 8px 0 0;
    }
    progress {
      width: 100%;
      height: 8px;
      accent-color: var(--ecole-primaire);
    }
    .cycles {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
    }
    .point-cycle {
      display: inline-block;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      margin-right: 4px;
    }
    .graphiques {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
      gap: 12px;
    }
    .graphique {
      padding: 16px;
      min-width: 0;
    }
    .graphique.large {
      grid-column: 1 / -1;
    }
    .graphique header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 4px 12px;
      margin-bottom: 8px;
    }
    .voir-donnees {
      min-height: 44px;
      padding: 0 8px;
      border: 0;
      background: none;
      color: var(--ecole-primaire);
      font: inherit;
      font-weight: 600;
      text-decoration: underline;
      cursor: pointer;
    }
    .zone {
      height: 280px;
      width: 100%;
    }
    .defilement {
      overflow-x: auto;
      margin-top: 12px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      padding: 8px;
      text-align: left;
      border-bottom: 1px solid var(--ecole-bordure);
    }
    thead th {
      color: var(--ecole-texte-secondaire);
      font-weight: 600;
    }
    tbody th {
      font-weight: 400;
    }
    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .erreur {
      padding: 16px;
    }
  `,
})
export class PageTableauDeBord {
  private readonly service = inject(ServiceStatistiques);

  protected readonly periodes = PERIODES;
  protected readonly cycles = CYCLES;

  protected readonly periode = signal<Periode>('ANNEE');
  protected readonly cycle = signal<Cycle | 'TOUS'>('TOUS');

  protected readonly tableau = signal<TableauDeBordDto | null>(null);
  protected readonly etat = signal<Etat>('chargement');
  protected readonly erreur = signal('');
  protected readonly donneesOuvertes = signal<ReadonlySet<number>>(new Set());

  /** Réponses plus anciennes que la dernière demande : ignorées. */
  private demande = 0;

  protected readonly vide = computed(() => {
    const t = this.tableau();
    const inscriptions = (t?.parStatut ?? []).reduce(
      (somme, s) => somme + (s.nombre ?? 0),
      0,
    );
    return inscriptions === 0;
  });

  protected readonly sousTitre = computed(() => {
    const t = this.tableau();
    if (!t?.anneeLibelle) {
      return null;
    }
    return `Année ${t.anneeLibelle}`;
  });

  protected readonly graphiques = computed<Graphique[]>(() => {
    const t = this.tableau();
    if (!t) {
      return [];
    }
    const police = this.police();
    const reduit = this.mouvementReduit();
    return [
      graphiqueNiveaux(t, police),
      graphiqueStatuts(t, police),
      graphiqueCourbe(t, police),
    ].map((g) => ({
      ...g,
      options: { ...g.options, animation: !reduit },
    }));
  });

  constructor() {
    effect(() => {
      this.periode();
      this.cycle();
      untracked(() => void this.charger());
    });
  }

  protected async charger(): Promise<void> {
    const numero = ++this.demande;
    this.etat.set('chargement');
    try {
      const t = await this.service.direction(
        this.periode(),
        this.cycle() === 'TOUS' ? undefined : (this.cycle() as Cycle),
      );
      if (numero === this.demande) {
        this.tableau.set(t);
        this.etat.set('pret');
      }
    } catch (e) {
      if (numero === this.demande) {
        this.erreur.set(messageErreur(e));
        this.etat.set('erreur');
      }
    }
  }

  protected basculerDonnees(index: number): void {
    this.donneesOuvertes.update((ouvertes) => {
      const suivantes = new Set(ouvertes);
      if (!suivantes.delete(index)) {
        suivantes.add(index);
      }
      return suivantes;
    });
  }

  /** 1 234 (séparateur de milliers français) ; évite d'importer le tuyau de nombre d'Angular. */
  protected entier(valeur: number): string {
    return new Intl.NumberFormat('fr-FR').format(valeur);
  }

  protected couleurCycle(
    cycle: keyof typeof COULEURS_CYCLES | undefined,
  ): string {
    return cycle ? COULEURS_CYCLES[cycle] : 'transparent';
  }

  private police(): string {
    return typeof document === 'undefined'
      ? 'sans-serif'
      : getComputedStyle(document.body).fontFamily || 'sans-serif';
  }

  private mouvementReduit(): boolean {
    return (
      typeof matchMedia === 'function' &&
      matchMedia('(prefers-reduced-motion: reduce)').matches
    );
  }
}
