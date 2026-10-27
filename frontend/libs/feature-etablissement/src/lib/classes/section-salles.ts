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
import { SalleDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceClasses } from '../services/service-classes';

/** Salles de l'école (nom unique, capacité). */
@Component({
  selector: 'ecole-section-salles',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-salles">
        {{ message }}
      </p>
    }
    <table data-testid="table-salles">
      <thead>
        <tr>
          <th>Salle</th>
          <th>Capacité</th>
          <th>Active</th>
          @if (droits.peutEcrire()) {
            <th></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (s of salles(); track s.id) {
          <tr [attr.data-testid]="'salle-' + s.nom">
            <td>{{ s.nom }}</td>
            <td>{{ s.capacite }}</td>
            <td>{{ s.actif ? 'Oui' : 'Non' }}</td>
            @if (droits.peutEcrire()) {
              <td>
                <button matButton type="button" (click)="modifier(s)">
                  Modifier
                </button>
                <button matButton type="button" (click)="supprimer(s)">
                  Supprimer
                </button>
              </td>
            }
          </tr>
        } @empty {
          <tr>
            <td colspan="4" class="aide">Aucune salle.</td>
          </tr>
        }
      </tbody>
    </table>

    @if (droits.peutEcrire()) {
      <h2>
        {{
          edition() ? 'Modifier la salle ' + edition()!.nom : 'Nouvelle salle'
        }}
      </h2>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="grille"
        data-testid="form-salle"
      >
        <mat-form-field>
          <mat-label>Nom</mat-label>
          <input matInput formControlName="nom" data-testid="champ-salle-nom" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Capacité</mat-label>
          <input
            matInput
            type="number"
            min="1"
            max="500"
            formControlName="capacite"
            data-testid="champ-salle-capacite"
          />
        </mat-form-field>
        @if (edition()) {
          <mat-checkbox formControlName="actif">Salle utilisée</mat-checkbox>
        }
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="enregistrer-salle"
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
export class SectionSalles {
  private readonly service = inject(ServiceClasses);
  protected readonly droits = inject(Droits);

  protected readonly salles = signal<SalleDto[]>([]);
  protected readonly edition = signal<SalleDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    nom: ['', [Validators.required, Validators.maxLength(60)]],
    capacite: [
      40,
      [Validators.required, Validators.min(1), Validators.max(500)],
    ],
    actif: [true],
  });

  constructor() {
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      this.salles.set(await this.service.salles());
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected modifier(salle: SalleDto): void {
    this.edition.set(salle);
    this.formulaire.reset({
      nom: salle.nom ?? '',
      capacite: salle.capacite ?? 40,
      actif: salle.actif ?? true,
    });
  }

  protected annuler(): void {
    this.edition.set(null);
    this.formulaire.reset({ nom: '', capacite: 40, actif: true });
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
        await this.service.modifierSalle(courante, v);
      } else {
        await this.service.creerSalle(v);
      }
      this.annuler();
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async supprimer(salle: SalleDto): Promise<void> {
    this.erreur.set(null);
    try {
      await this.service.supprimerSalle(salle);
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }
}
