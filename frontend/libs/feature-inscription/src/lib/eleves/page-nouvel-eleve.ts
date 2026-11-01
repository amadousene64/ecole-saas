import { EnTetePage } from '@ecole/ui';
import { Component, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { messageErreur } from '../commun/erreurs';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceEleves } from '../services/service-eleves';

/** Création d'un élève (le matricule est attribué par le serveur), puis ouverture de sa fiche. */
@Component({
  selector: 'ecole-page-nouvel-eleve',
  imports: [
    EnTetePage,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <p><a routerLink="/eleves">← Retour à la liste</a></p>
    <ecole-en-tete-page
      titre="Nouvel élève"
      sousTitre="Le matricule est attribué à la création"
    />
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-creation">
        {{ message }}
      </p>
    }
    <form
      [formGroup]="formulaire"
      (ngSubmit)="creer()"
      class="grille"
      data-testid="form-nouvel-eleve"
    >
      <mat-form-field appearance="outline">
        <mat-label>Prénoms</mat-label>
        <input matInput formControlName="prenoms" data-testid="champ-prenoms" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Nom</mat-label>
        <input matInput formControlName="nom" data-testid="champ-nom" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Sexe</mat-label>
        <mat-select formControlName="sexe" data-testid="champ-sexe">
          <mat-option value="F">Féminin</mat-option>
          <mat-option value="M">Masculin</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Date de naissance</mat-label>
        <input
          matInput
          type="date"
          formControlName="dateNaissance"
          data-testid="champ-naissance"
        />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Lieu de naissance</mat-label>
        <input
          matInput
          formControlName="lieuNaissance"
          data-testid="champ-lieu"
        />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Nationalité (code pays)</mat-label>
        <input matInput formControlName="nationalite" maxlength="2" />
      </mat-form-field>
      <div class="actions">
        <button
          matButton="filled"
          type="submit"
          [disabled]="occupe()"
          data-testid="creer-eleve"
        >
          Créer l'élève
        </button>
      </div>
    </form>
  `,
  styles: [STYLES_COMMUNS],
})
export class PageNouvelEleve {
  private readonly service = inject(ServiceEleves);
  private readonly routeur = inject(Router);

  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    prenoms: ['', [Validators.required, Validators.maxLength(100)]],
    nom: ['', [Validators.required, Validators.maxLength(100)]],
    sexe: ['F' as 'M' | 'F', Validators.required],
    dateNaissance: ['', Validators.required],
    lieuNaissance: ['', Validators.required],
    nationalite: ['SN', [Validators.required, Validators.maxLength(2)]],
  });

  protected async creer(): Promise<void> {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const eleve = await this.service.creer(this.formulaire.getRawValue());
      await this.routeur.navigate(['/eleves', eleve.id]);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
