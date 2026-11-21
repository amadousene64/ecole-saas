import {
  Component,
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
import { PeriodeDto } from '@ecole/api-client';
import { messageErreur } from '../commun/erreurs';
import { ServiceAnnees } from '../services/service-annees';

/** Une période (trimestre ou semestre) : libellé et dates ajustables, sans chevauchement. */
@Component({
  selector: 'ecole-ligne-periode',
  imports: [ReactiveFormsModule, MatButtonModule],
  host: { role: 'group', '[attr.aria-label]': '"Période " + periode().numero' },
  template: `
    <span class="numero">{{ periode().numero }}</span>
    <input
      class="champ"
      [formControl]="formulaire.controls.libelle"
      aria-label="Libellé de la période"
      [attr.data-testid]="'periode-libelle-' + periode().numero"
    />
    <input
      class="champ"
      type="date"
      [formControl]="formulaire.controls.dateDebut"
      aria-label="Début de la période"
      [attr.data-testid]="'periode-debut-' + periode().numero"
    />
    <input
      class="champ"
      type="date"
      [formControl]="formulaire.controls.dateFin"
      aria-label="Fin de la période"
      [attr.data-testid]="'periode-fin-' + periode().numero"
    />
    @if (modifiable()) {
      <button
        matButton
        type="button"
        [disabled]="occupe() || formulaire.invalid"
        (click)="enregistrer()"
        [attr.data-testid]="'periode-enregistrer-' + periode().numero"
      >
        Enregistrer
      </button>
    }
    @if (probleme(); as message) {
      <span class="probleme" role="alert">{{ message }}</span>
    }
  `,
  styleUrl: './ligne-periode.scss',
})
export class LignePeriode {
  private readonly service = inject(ServiceAnnees);

  readonly periode = input.required<PeriodeDto>();
  readonly modifiable = input(false);
  readonly modifiee = output<PeriodeDto>();

  protected readonly probleme = signal<string | null>(null);
  protected readonly occupe = signal(false);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    libelle: ['', [Validators.required, Validators.maxLength(40)]],
    dateDebut: ['', Validators.required],
    dateFin: ['', Validators.required],
  });

  constructor() {
    effect(() => {
      const p = this.periode();
      this.formulaire.reset({
        libelle: p.libelle ?? '',
        dateDebut: p.dateDebut ?? '',
        dateFin: p.dateFin ?? '',
      });
      if (this.modifiable()) {
        this.formulaire.enable({ emitEvent: false });
      } else {
        this.formulaire.disable({ emitEvent: false });
      }
    });
  }

  protected async enregistrer(): Promise<void> {
    this.probleme.set(null);
    this.occupe.set(true);
    try {
      this.modifiee.emit(
        await this.service.modifierPeriode(
          this.periode(),
          this.formulaire.getRawValue(),
        ),
      );
    } catch (e) {
      this.probleme.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
