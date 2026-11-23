import {
  Component,
  inject,
  input,
  OnInit,
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
import { AffectationDto } from '@ecole/api-client';
import { Champ, Modale } from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
import {
  libelleAffectation,
  ServiceAffectations,
} from '../services/service-affectations';

let suivant = 0;

/**
 * Période d'une affectation : corriger le début, ou prévoir la fin à la date d'un remplacement
 * (le remplaçant est affecté à partir de cette même date).
 */
@Component({
  selector: 'ecole-modale-periode',
  imports: [
    Champ,
    Modale,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <ecole-modale
      titre="Modifier la période"
      [sousTitre]="libelle()"
      testId="modale-periode"
      [ouverte]="true"
      [modifiee]="formulaire.dirty"
      (fermee)="fermee.emit()"
    >
      @if (erreur(); as message) {
        <p class="message erreur" role="alert" data-testid="erreur-periode">
          {{ message }}
        </p>
      }
      <form
        [id]="idFormulaire"
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="formulaire"
      >
        <ecole-champ libelle="Début" class="champ-court">
          <mat-form-field appearance="outline">
            <input
              matInput
              type="date"
              formControlName="debut"
              data-testid="champ-periode-debut"
            />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Prend fin le" class="champ-court">
          <mat-form-field appearance="outline">
            <input
              matInput
              type="date"
              formControlName="fin"
              data-testid="champ-periode-fin"
            />
            <mat-hint>Premier jour où elle ne vaut plus</mat-hint>
          </mat-form-field>
        </ecole-champ>
      </form>
      <div pied>
        <button matButton="outlined" type="button" (click)="fermee.emit()">
          Annuler
        </button>
        <button
          matButton="filled"
          type="submit"
          [attr.form]="idFormulaire"
          [disabled]="occupe()"
          data-testid="enregistrer-periode"
        >
          Enregistrer
        </button>
      </div>
    </ecole-modale>
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class ModalePeriode implements OnInit {
  private readonly service = inject(ServiceAffectations);

  readonly affectation = input.required<AffectationDto>();
  readonly enregistree = output<void>();
  readonly fermee = output<void>();

  protected readonly idFormulaire = `form-periode-${++suivant}`;
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    debut: ['', Validators.required],
    fin: [''],
  });

  ngOnInit(): void {
    this.formulaire.reset({
      debut: this.affectation().debut ?? '',
      fin: this.affectation().fin ?? '',
    });
  }

  protected libelle(): string {
    return libelleAffectation(this.affectation());
  }

  protected async enregistrer(): Promise<void> {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      await this.service.changerPeriode(
        this.affectation(),
        v.debut,
        v.fin || undefined,
      );
      this.enregistree.emit();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
