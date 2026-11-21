import { EnTetePage, CHAMPS_FORMULAIRE, Champ, NATIONALITES } from '@ecole/ui';
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
import { ServiceEleves } from '../services/service-eleves';

/** Création d'un élève (le matricule est attribué par le serveur), puis ouverture de sa fiche. */
@Component({
  selector: 'ecole-page-nouvel-eleve',
  providers: [CHAMPS_FORMULAIRE],
  imports: [
    Champ,
    EnTetePage,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <div class="colonne-formulaire">
      <a class="retour" routerLink="/eleves">← Retour à la liste</a>
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
        class="formulaire carte"
        data-testid="form-nouvel-eleve"
      >
        <ecole-champ libelle="Prénoms">
          <mat-form-field appearance="outline">
            <input
              matInput
              formControlName="prenoms"
              data-testid="champ-prenoms"
            />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Nom">
          <mat-form-field appearance="outline">
            <input matInput formControlName="nom" data-testid="champ-nom" />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Sexe">
          <mat-form-field appearance="outline">
            <mat-select formControlName="sexe" data-testid="champ-sexe">
              <mat-option value="F">Féminin</mat-option>
              <mat-option value="M">Masculin</mat-option>
            </mat-select>
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Date de naissance" class="champ-court">
          <mat-form-field appearance="outline">
            <input
              matInput
              type="date"
              formControlName="dateNaissance"
              data-testid="champ-naissance"
            />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Lieu de naissance">
          <mat-form-field appearance="outline">
            <input
              matInput
              formControlName="lieuNaissance"
              data-testid="champ-lieu"
            />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Nationalité">
          <mat-form-field appearance="outline">
            <mat-select
              formControlName="nationalite"
              data-testid="champ-nationalite"
            >
              @for (n of nationalites; track n.code) {
                <mat-option [value]="n.code">{{ n.libelle }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </ecole-champ>
        <div class="actions">
          <a matButton="outlined" routerLink="/eleves">Annuler</a>
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
    </div>
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class PageNouvelEleve {
  private readonly service = inject(ServiceEleves);
  private readonly routeur = inject(Router);

  protected readonly nationalites = NATIONALITES;
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
