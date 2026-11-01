import { Component, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SerieDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceStructure } from '../services/service-structure';

/** Séries du lycée (L1, S2…), modifiables par l'école. Les niveaux qui les proposent : voir Niveaux. */
@Component({
  selector: 'ecole-section-series',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-series">
        {{ message }}
      </p>
    }
    <table data-testid="table-series" class="cartes">
      <thead>
        <tr>
          <th>Code</th>
          <th>Libellé</th>
          <th>Actif</th>
          @if (droits.peutEcrire()) {
            <th></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (s of series(); track s.id) {
          <tr [attr.data-testid]="'serie-' + s.code">
            <td data-label="Code">{{ s.code }}</td>
            <td data-label="Libellé">{{ s.libelle }}</td>
            <td data-label="Actif">{{ s.actif ? 'Oui' : 'Non' }}</td>
            @if (droits.peutEcrire()) {
              <td data-label="Actions">
                <button matButton type="button" (click)="modifier(s)">
                  Modifier
                </button>
                <button matButton type="button" (click)="supprimer(s)">
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
          edition() ? 'Modifier la série ' + edition()!.code : 'Nouvelle série'
        }}
      </h2>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="grille"
        data-testid="form-serie"
      >
        <mat-form-field appearance="outline">
          <mat-label>Code</mat-label>
          <input
            matInput
            formControlName="code"
            data-testid="champ-serie-code"
          />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Libellé</mat-label>
          <input
            matInput
            formControlName="libelle"
            data-testid="champ-serie-libelle"
          />
        </mat-form-field>
        @if (edition()) {
          <mat-checkbox formControlName="actif">Série utilisée</mat-checkbox>
        }
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="enregistrer-serie"
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
  styles: [STYLES_COMMUNS],
})
export class SectionSeries {
  private readonly service = inject(ServiceStructure);
  protected readonly droits = inject(Droits);

  protected readonly series = signal<SerieDto[]>([]);
  protected readonly edition = signal<SerieDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    code: ['', [Validators.required, Validators.maxLength(10)]],
    libelle: ['', [Validators.required, Validators.maxLength(80)]],
    actif: [true],
  });

  constructor() {
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      this.series.set(await this.service.series());
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected modifier(serie: SerieDto): void {
    this.edition.set(serie);
    this.formulaire.reset({
      code: serie.code ?? '',
      libelle: serie.libelle ?? '',
      actif: serie.actif ?? true,
    });
  }

  protected annuler(): void {
    this.edition.set(null);
    this.formulaire.reset({ code: '', libelle: '', actif: true });
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
      const courante = this.edition();
      if (courante) {
        await this.service.modifierSerie(courante, v);
      } else {
        await this.service.creerSerie(v);
      }
      this.annuler();
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async supprimer(serie: SerieDto): Promise<void> {
    this.erreur.set(null);
    try {
      await this.service.supprimerSerie(serie);
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }
}
