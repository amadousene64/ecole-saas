import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { CreneauDto, CycleDto } from '@ecole/api-client';
import { Champ, Confirmation, Pastille } from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { requis } from '../commun/requis';
import { ServiceEnseignements } from '../services/service-enseignements';
import { ServiceStructure } from '../services/service-structure';
import { heure, libelleJours, ModaleCreneau } from './modale-creneau';

/**
 * Grille horaire de chaque cycle : créneaux de cours (un appel chacun) et de pause, par heure de
 * début. Une grille vide peut recopier celle d'un autre cycle.
 */
@Component({
  selector: 'ecole-section-grille',
  imports: [
    Champ,
    Confirmation,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    ModaleCreneau,
    Pastille,
  ],
  template: `
    <div class="filtres-liste" role="search" aria-label="Choix du cycle">
      <ecole-champ libelle="Cycle">
        <mat-form-field appearance="outline">
          <mat-select
            [value]="cycleId()"
            (selectionChange)="choisirCycle($event.value)"
            data-testid="filtre-cycle"
          >
            @for (c of cycles(); track c.id) {
              <mat-option [value]="c.id">{{ c.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </ecole-champ>
    </div>
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-grille">
        {{ message }}
      </p>
    }
    @if (droits.peutOrganiser() && cycleId()) {
      <div class="actions">
        <button
          matButton="filled"
          type="button"
          (click)="edition.set({ creneau: null })"
          data-testid="ajouter-creneau"
        >
          Ajouter un créneau
        </button>
      </div>
    }
    <table data-testid="table-creneaux" class="cartes">
      <thead>
        <tr>
          <th>Horaire</th>
          <th>Jours</th>
          <th>Type</th>
          <th>Libellé</th>
          @if (droits.peutOrganiser()) {
            <th><span class="sr-only">Actions</span></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (c of creneaux(); track c.id) {
          <tr [attr.data-testid]="'creneau-' + heure(c.debut)">
            <td data-label="Horaire" class="mono">
              {{ heure(c.debut) }} – {{ heure(c.fin) }}
            </td>
            <td data-label="Jours">{{ libelleJours(c.jours) }}</td>
            <td data-label="Type">
              @if (c.type === 'PAUSE') {
                <ecole-pastille ton="neutre">Pause</ecole-pastille>
              } @else {
                Cours
              }
            </td>
            <td data-label="Libellé">{{ c.libelle ?? '' }}</td>
            @if (droits.peutOrganiser()) {
              <td data-label="Actions">
                <button
                  matButton
                  type="button"
                  (click)="edition.set({ creneau: c })"
                >
                  Modifier<span class="sr-only">
                    le créneau de {{ heure(c.debut) }}</span
                  >
                </button>
                <button matButton type="button" (click)="aSupprimer.set(c)">
                  Supprimer<span class="sr-only">
                    le créneau de {{ heure(c.debut) }}</span
                  >
                </button>
              </td>
            }
          </tr>
        } @empty {
          <tr>
            <td colspan="5" class="aide">Aucun créneau pour ce cycle.</td>
          </tr>
        }
      </tbody>
    </table>

    @if (droits.peutOrganiser() && creneaux().length === 0 && autres().length) {
      <section class="carte" aria-labelledby="titre-copie">
        <h2 id="titre-copie">Reprendre la grille d'un autre cycle</h2>
        <div class="filtres-liste">
          <ecole-champ libelle="Copier la grille de">
            <mat-form-field appearance="outline">
              <mat-select
                [value]="source()"
                (selectionChange)="source.set($event.value)"
                data-testid="source-copie"
              >
                @for (c of autres(); track c.id) {
                  <mat-option [value]="c.id">{{ c.libelle }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </ecole-champ>
        </div>
        <button
          matButton="outlined"
          type="button"
          [disabled]="!source() || occupe()"
          (click)="copier()"
          data-testid="copier-grille"
        >
          Copier la grille
        </button>
      </section>
    }

    @if (edition(); as e) {
      <ecole-modale-creneau
        [cycleId]="cycleId()!"
        [libelleCycle]="cycleCourant()?.libelle ?? ''"
        [creneau]="e.creneau"
        (enregistre)="enregistre()"
        (fermee)="edition.set(null)"
      />
    }
    @if (aSupprimer(); as c) {
      <ecole-confirmation
        titre="Supprimer le créneau"
        [consequence]="
          'Le créneau de ' +
          heure(c.debut) +
          ' à ' +
          heure(c.fin) +
          ' (' +
          libelleJours(c.jours) +
          ') est retiré de la grille.'
        "
        action="Supprimer"
        testId="confirmer-suppression-creneau"
        [ouverte]="true"
        [occupe]="occupe()"
        [erreur]="erreurSuppression()"
        (confirmee)="supprimer(c)"
        (annulee)="aSupprimer.set(null)"
      />
    }
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class SectionGrille {
  private readonly service = inject(ServiceEnseignements);
  private readonly structure = inject(ServiceStructure);
  protected readonly droits = inject(Droits);
  protected readonly heure = heure;
  protected readonly libelleJours = libelleJours;

  protected readonly cycles = signal<CycleDto[]>([]);
  protected readonly cycleId = signal<string | null>(null);
  protected readonly creneaux = signal<CreneauDto[]>([]);
  protected readonly edition = signal<{ creneau: CreneauDto | null } | null>(
    null,
  );
  protected readonly aSupprimer = signal<CreneauDto | null>(null);
  protected readonly source = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurSuppression = signal<string | null>(null);
  protected readonly occupe = signal(false);

  protected readonly cycleCourant = computed(
    () => this.cycles().find((c) => c.id === this.cycleId()) ?? null,
  );
  /** Cycles dont la grille peut être recopiée dans le cycle affiché. */
  protected readonly autres = computed(() =>
    this.cycles().filter((c) => c.id !== this.cycleId()),
  );

  constructor() {
    void this.chargerCycles();
  }

  private async chargerCycles(): Promise<void> {
    try {
      const cycles = await this.structure.cycles();
      this.cycles.set(cycles);
      if (cycles.length) {
        await this.choisirCycle(requis(cycles[0].id));
      }
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected async choisirCycle(id: string): Promise<void> {
    this.cycleId.set(id);
    this.source.set(null);
    await this.chargerCreneaux();
  }

  private async chargerCreneaux(): Promise<void> {
    const id = this.cycleId();
    if (!id) {
      return;
    }
    this.erreur.set(null);
    try {
      this.creneaux.set(await this.service.creneaux(id));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected async enregistre(): Promise<void> {
    this.edition.set(null);
    await this.chargerCreneaux();
  }

  protected async copier(): Promise<void> {
    const vers = this.cycleId();
    const depuis = this.source();
    if (!vers || !depuis) {
      return;
    }
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      this.creneaux.set(await this.service.copierGrille(vers, depuis));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async supprimer(c: CreneauDto): Promise<void> {
    this.erreurSuppression.set(null);
    this.occupe.set(true);
    try {
      await this.service.supprimerCreneau(c);
      this.aSupprimer.set(null);
      await this.chargerCreneaux();
    } catch (e) {
      this.erreurSuppression.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
