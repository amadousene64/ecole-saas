import {
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CycleDto } from '@ecole/api-client';
import { messageErreur } from '../commun/erreurs';
import {
  baseCompatible,
  LIBELLES_CYCLE,
  ModeNotation,
  MODES_NOTATION,
  TypePeriode,
  TYPES_PERIODE,
} from '../commun/etiquettes';
import { ServiceStructure } from '../services/service-structure';

/**
 * Paramètres de notation d'un cycle. C'est le MODÈLE : une année en copie les valeurs à son
 * ouverture et les garde ensuite, donc modifier ici n'agit que sur les années à venir.
 */
@Component({
  selector: 'ecole-carte-cycle',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  template: `
    <form
      class="cycle"
      [formGroup]="formulaire"
      (ngSubmit)="enregistrer()"
      [attr.data-testid]="'cycle-' + cycle().code"
    >
      <h3>{{ nomCycle() }}</h3>
      <div class="champs">
        <mat-form-field appearance="outline">
          <mat-label>Libellé</mat-label>
          <input matInput formControlName="libelle" required />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Découpage de l'année</mat-label>
          <mat-select
            formControlName="typePeriode"
            data-testid="champ-type-periode"
          >
            @for (t of types; track t.valeur) {
              <mat-option [value]="t.valeur">{{ t.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Mode de notation</mat-label>
          <mat-select
            formControlName="modeNotation"
            data-testid="champ-mode-notation"
          >
            @for (m of modes; track m.valeur) {
              <mat-option [value]="m.valeur">{{ m.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Base de la moyenne</mat-label>
          <mat-select
            formControlName="baseMoyenne"
            data-testid="champ-base-moyenne"
          >
            <mat-option [value]="10">Sur 10</mat-option>
            <mat-option [value]="20">Sur 20</mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      @if (incompatible()) {
        <p class="message erreur" role="alert" data-testid="base-incompatible">
          La base 10 n'est possible qu'avec la notation par barème : les
          coefficients sont sur 20.
        </p>
      }
      @if (probleme(); as message) {
        <p class="message erreur" role="alert">{{ message }}</p>
      }
      @if (confirmation()) {
        <p class="message ok" role="status">Cycle enregistré.</p>
      }
      @if (modifiable()) {
        <button
          matButton="filled"
          type="submit"
          [disabled]="occupe() || incompatible()"
          data-testid="enregistrer-cycle"
        >
          Enregistrer
        </button>
      }
    </form>
  `,
  styles: `
    .cycle {
      background: var(--ecole-surface);
      border: 1px solid var(--ecole-bordure);
      border-radius: var(--ecole-rayon-carte);
      padding: 8px 16px 12px;
      margin-bottom: 16px;
    }
    h3 {
      margin: 8px 0;
    }
    .champs {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 0 16px;
    }
    .message {
      padding: 6px 10px;
      border-radius: 4px;
    }
    .message.erreur {
      background: var(--mat-sys-error-container, #fdd);
    }
    .message.ok {
      background: var(--mat-sys-secondary-container, #dfd);
    }
  `,
})
export class CarteCycle {
  private readonly service = inject(ServiceStructure);

  readonly cycle = input.required<CycleDto>();
  readonly modifiable = input(false);
  readonly enregistre = output<CycleDto>();

  protected readonly types = TYPES_PERIODE;
  protected readonly modes = MODES_NOTATION;
  protected readonly probleme = signal<string | null>(null);
  protected readonly confirmation = signal(false);
  protected readonly occupe = signal(false);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    libelle: ['', [Validators.required, Validators.maxLength(80)]],
    typePeriode: ['SEMESTRE' as TypePeriode],
    modeNotation: ['COEFFICIENT' as ModeNotation],
    baseMoyenne: [20],
  });

  protected readonly nomCycle = computed(
    () => LIBELLES_CYCLE[this.cycle().code ?? ''] ?? this.cycle().libelle,
  );

  /** Vrai quand la combinaison choisie viole la règle « base 10 = barème seulement ». */
  protected readonly incompatible = signal(false);

  constructor() {
    this.formulaire.valueChanges.subscribe((v) =>
      this.incompatible.set(
        !baseCompatible(v.modeNotation ?? 'COEFFICIENT', v.baseMoyenne ?? 20),
      ),
    );
    // Les valeurs viennent de l'entrée ; la lecture seule désactive les champs.
    effect(() => {
      const c = this.cycle();
      this.formulaire.reset({
        libelle: c.libelle ?? '',
        typePeriode: c.typePeriode ?? 'SEMESTRE',
        modeNotation: c.modeNotation ?? 'COEFFICIENT',
        baseMoyenne: c.baseMoyenne ?? 20,
      });
      if (this.modifiable()) {
        this.formulaire.enable({ emitEvent: false });
      } else {
        this.formulaire.disable({ emitEvent: false });
      }
    });
  }

  protected async enregistrer(): Promise<void> {
    if (this.formulaire.invalid || this.incompatible()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.probleme.set(null);
    this.confirmation.set(false);
    this.occupe.set(true);
    try {
      const modifie = await this.service.modifierCycle(
        this.cycle(),
        this.formulaire.getRawValue(),
      );
      this.confirmation.set(true);
      this.enregistre.emit(modifie);
    } catch (e) {
      this.probleme.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
