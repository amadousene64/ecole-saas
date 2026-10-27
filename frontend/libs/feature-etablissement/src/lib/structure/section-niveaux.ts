import { Component, computed, effect, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CycleDto, NiveauDto, SerieDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { baseCompatible, LIBELLES_CYCLE } from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceStructure } from '../services/service-structure';

/** Niveaux d'études (CI à Terminale), leur base de moyenne éventuelle et leurs séries. */
@Component({
  selector: 'ecole-section-niveaux',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-niveaux">
        {{ message }}
      </p>
    }
    <table data-testid="table-niveaux">
      <thead>
        <tr>
          <th>Code</th>
          <th>Libellé</th>
          <th>Cycle</th>
          <th>Base de la moyenne</th>
          <th>Séries</th>
          <th>Actif</th>
          @if (droits.peutEcrire()) {
            <th><span class="sr-only">Actions</span></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (n of niveaux(); track n.id) {
          <tr [attr.data-testid]="'niveau-' + n.code">
            <td>{{ n.code }}</td>
            <td>{{ n.libelle }}</td>
            <td>{{ nomCycle(n.cycleId) }}</td>
            <td>
              Sur {{ n.baseMoyenneEffective }}
              @if (n.baseMoyenne) {
                <span class="pastille">propre au niveau</span>
              }
            </td>
            <td>{{ codesSeries(n) }}</td>
            <td>{{ n.actif ? 'Oui' : 'Non' }}</td>
            @if (droits.peutEcrire()) {
              <td>
                <button matButton type="button" (click)="modifier(n)">
                  Modifier
                </button>
                <button matButton type="button" (click)="supprimer(n)">
                  Supprimer
                </button>
              </td>
            }
          </tr>
        }
      </tbody>
    </table>

    @if (droits.peutEcrire()) {
      <h2>
        {{
          edition() ? 'Modifier le niveau ' + edition()!.code : 'Nouveau niveau'
        }}
      </h2>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="grille"
        data-testid="form-niveau"
      >
        @if (!edition()) {
          <mat-form-field>
            <mat-label>Cycle</mat-label>
            <mat-select
              formControlName="cycleId"
              data-testid="champ-niveau-cycle"
            >
              @for (c of cycles(); track c.id) {
                <mat-option [value]="c.id">{{ nomCycle(c.id) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Code</mat-label>
            <input
              matInput
              formControlName="code"
              data-testid="champ-niveau-code"
            />
          </mat-form-field>
        }
        <mat-form-field>
          <mat-label>Libellé</mat-label>
          <input
            matInput
            formControlName="libelle"
            data-testid="champ-niveau-libelle"
          />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Ordre</mat-label>
          <input matInput type="number" min="1" formControlName="ordre" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Base de la moyenne</mat-label>
          <mat-select formControlName="base" data-testid="champ-niveau-base">
            <mat-option [value]="0">Suit celle du cycle</mat-option>
            <mat-option [value]="10">Sur 10</mat-option>
            <mat-option [value]="20">Sur 20</mat-option>
          </mat-select>
          @if (!droits.estDirecteur()) {
            <mat-hint>Réservé au directeur.</mat-hint>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Séries proposées</mat-label>
          <mat-select
            formControlName="serieIds"
            multiple
            data-testid="champ-niveau-series"
          >
            @for (s of series(); track s.id) {
              <mat-option [value]="s.id">{{ s.code }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (edition()) {
          <mat-checkbox formControlName="actif">Niveau utilisé</mat-checkbox>
        }
        @if (baseIncompatible()) {
          <p
            class="message erreur"
            role="alert"
            data-testid="niveau-base-incompatible"
          >
            La base 10 n'est possible que dans un cycle noté par barème.
          </p>
        }
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe() || baseIncompatible()"
            data-testid="enregistrer-niveau"
          >
            {{ edition() ? 'Enregistrer' : 'Ajouter' }}
          </button>
          @if (edition()) {
            <button matButton type="button" (click)="annuler()">Annuler</button>
          }
        </div>
      </form>
    }
  `,
  styles: [
    STYLES_COMMUNS,
    `
      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip-path: inset(50%);
      }
    `,
  ],
})
export class SectionNiveaux {
  private readonly service = inject(ServiceStructure);
  protected readonly droits = inject(Droits);

  protected readonly niveaux = signal<NiveauDto[]>([]);
  protected readonly cycles = signal<CycleDto[]>([]);
  protected readonly series = signal<SerieDto[]>([]);
  protected readonly edition = signal<NiveauDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly baseIncompatible = signal(false);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    cycleId: ['', Validators.required],
    code: ['', [Validators.required, Validators.maxLength(10)]],
    libelle: ['', [Validators.required, Validators.maxLength(80)]],
    ordre: [1, [Validators.required, Validators.min(1)]],
    /** 0 : suit le cycle ; 10 ou 20 : surcharge du niveau. */
    base: [0],
    serieIds: [[] as string[]],
    actif: [true],
  });

  private readonly cyclesParId = computed(
    () => new Map(this.cycles().map((c) => [c.id, c])),
  );

  constructor() {
    effect(() => {
      // La base propre au niveau est un paramètre de notation : directeur seul.
      if (this.droits.estDirecteur()) {
        this.formulaire.controls.base.enable({ emitEvent: false });
      } else {
        this.formulaire.controls.base.disable({ emitEvent: false });
      }
    });
    this.formulaire.valueChanges.subscribe(() => this.verifierBase());
    void this.charger();
  }

  protected nomCycle(id: string | undefined): string {
    const c = this.cyclesParId().get(id);
    return c ? (LIBELLES_CYCLE[c.code ?? ''] ?? c.libelle ?? '') : '';
  }

  protected codesSeries(n: NiveauDto): string {
    const codes = (n.serieIds ?? [])
      .map((id) => this.series().find((s) => s.id === id)?.code)
      .filter((code): code is string => !!code);
    return codes.length ? codes.join(', ') : '—';
  }

  private verifierBase(): void {
    const v = this.formulaire.getRawValue();
    const cycle = this.cyclesParId().get(this.edition()?.cycleId ?? v.cycleId);
    this.baseIncompatible.set(
      !!cycle &&
        !baseCompatible(cycle.modeNotation ?? 'COEFFICIENT', v.base || null),
    );
  }

  private async charger(): Promise<void> {
    try {
      const [niveaux, cycles, series] = await Promise.all([
        this.service.niveaux(),
        this.service.cycles(),
        this.service.series(),
      ]);
      this.niveaux.set(niveaux);
      this.cycles.set(cycles);
      this.series.set(series);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected modifier(niveau: NiveauDto): void {
    this.edition.set(niveau);
    this.formulaire.reset({
      cycleId: niveau.cycleId ?? '',
      code: niveau.code ?? '',
      libelle: niveau.libelle ?? '',
      ordre: niveau.ordre ?? 1,
      base: niveau.baseMoyenne ?? 0,
      serieIds: niveau.serieIds ?? [],
      actif: niveau.actif ?? true,
    });
    this.formulaire.controls.cycleId.disable({ emitEvent: false });
    this.formulaire.controls.code.disable({ emitEvent: false });
  }

  protected annuler(): void {
    this.edition.set(null);
    this.formulaire.controls.cycleId.enable({ emitEvent: false });
    this.formulaire.controls.code.enable({ emitEvent: false });
    this.formulaire.reset({ base: 0, ordre: 1, actif: true, serieIds: [] });
  }

  protected async enregistrer(): Promise<void> {
    if (this.formulaire.invalid || this.baseIncompatible()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    const base = v.base === 0 ? undefined : v.base;
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const courant = this.edition();
      let enregistre: NiveauDto;
      if (courant) {
        enregistre = await this.service.modifierNiveau(courant, {
          libelle: v.libelle,
          ordre: v.ordre,
          baseMoyenne: this.droits.estDirecteur() ? base : courant.baseMoyenne,
          actif: v.actif,
        });
      } else {
        enregistre = await this.service.creerNiveau({
          cycleId: v.cycleId,
          code: v.code,
          libelle: v.libelle,
          ordre: v.ordre,
          baseMoyenne: this.droits.estDirecteur() ? base : undefined,
        });
      }
      const avant = [...(enregistre.serieIds ?? [])].sort().join();
      if ([...v.serieIds].sort().join() !== avant) {
        enregistre = await this.service.definirSeries(enregistre, v.serieIds);
      }
      this.annuler();
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async supprimer(niveau: NiveauDto): Promise<void> {
    this.erreur.set(null);
    try {
      await this.service.supprimerNiveau(niveau);
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }
}
