import { Component, effect, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { EtablissementDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import {
  STATUTS_ETABLISSEMENT,
  StatutEtablissement,
} from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import {
  ServiceEtablissement,
  TypeImage,
} from '../services/service-etablissement';
import { CarteImage } from './carte-image';

/** Profil de l'école et images officielles. Lecture seule sans le droit d'écriture. */
@Component({
  selector: 'ecole-page-parametres',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    CarteImage,
  ],
  template: `
    <h1>Paramètres de l'établissement</h1>
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur">
        {{ message }}
      </p>
    }
    @if (confirmation(); as message) {
      <p class="message ok" role="status" data-testid="confirmation">
        {{ message }}
      </p>
    }
    @if (!droits.peutEcrire()) {
      <p class="aide" data-testid="lecture-seule">
        Lecture seule : seuls le directeur et le secrétariat modifient ces
        informations.
      </p>
    }

    <form
      [formGroup]="formulaire"
      (ngSubmit)="enregistrer()"
      class="grille"
      data-testid="profil"
    >
      <mat-form-field appearance="outline">
        <mat-label>Nom</mat-label>
        <input
          matInput
          formControlName="nom"
          required
          data-testid="champ-nom"
        />
        @if (formulaire.controls.nom.hasError('required')) {
          <mat-error>Le nom est obligatoire.</mat-error>
        }
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input
          matInput
          formControlName="code"
          required
          data-testid="champ-code"
        />
        @if (formulaire.controls.code.hasError('pattern')) {
          <mat-error>Lettres, chiffres, points et tirets seulement.</mat-error>
        }
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Statut</mat-label>
        <mat-select formControlName="statut" data-testid="champ-statut">
          @for (s of statuts; track s.valeur) {
            <mat-option [value]="s.valeur">{{ s.libelle }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Inspection d'académie (IA)</mat-label>
        <input matInput formControlName="ia" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Inspection de l'éducation (IEF)</mat-label>
        <input matInput formControlName="ief" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Adresse</mat-label>
        <input matInput formControlName="adresse" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Téléphone</mat-label>
        <input
          matInput
          formControlName="telephone"
          inputmode="tel"
          data-testid="champ-telephone"
        />
        <mat-hint
          >Format international ; +221 par défaut pour 9 chiffres.</mat-hint
        >
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>E-mail</mat-label>
        <input matInput type="email" formControlName="email" />
        @if (formulaire.controls.email.hasError('email')) {
          <mat-error>Adresse e-mail invalide.</mat-error>
        }
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Fuseau horaire</mat-label>
        <input matInput formControlName="fuseau" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Devise</mat-label>
        <input matInput formControlName="devise" maxlength="3" />
      </mat-form-field>
      @if (droits.peutEcrire()) {
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="enregistrer"
          >
            Enregistrer
          </button>
        </div>
      }
    </form>

    <h2>Images officielles</h2>
    <div class="grille" data-testid="images">
      <ecole-carte-image
        type="LOGO"
        titre="Logo"
        [url]="etablissement()?.logoUrl"
        [modifiable]="droits.peutEcrire()"
        (deposer)="deposer('LOGO', $event)"
        (supprimer)="supprimer('LOGO')"
      />
      @if (droits.estDirecteur()) {
        <ecole-carte-image
          type="CACHET"
          titre="Cachet"
          [url]="etablissement()?.cachetUrl"
          [modifiable]="true"
          (deposer)="deposer('CACHET', $event)"
          (supprimer)="supprimer('CACHET')"
        />
        <ecole-carte-image
          type="SIGNATURE"
          titre="Signature du chef d'établissement"
          [url]="etablissement()?.signatureUrl"
          [modifiable]="true"
          (deposer)="deposer('SIGNATURE', $event)"
          (supprimer)="supprimer('SIGNATURE')"
        />
      }
    </div>
    @if (!droits.estDirecteur()) {
      <p class="aide">Le cachet et la signature sont réservés au directeur.</p>
    }
  `,
  styles: [STYLES_COMMUNS],
})
export class PageParametres {
  private readonly service = inject(ServiceEtablissement);
  protected readonly droits = inject(Droits);
  protected readonly statuts = STATUTS_ETABLISSEMENT;

  protected readonly etablissement = signal<EtablissementDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly confirmation = signal<string | null>(null);
  protected readonly occupe = signal(false);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    nom: ['', [Validators.required, Validators.maxLength(200)]],
    code: [
      '',
      [
        Validators.required,
        Validators.maxLength(40),
        Validators.pattern(/^[A-Za-z0-9._-]+$/),
      ],
    ],
    statut: ['PUBLIC' as StatutEtablissement],
    ia: [''],
    ief: [''],
    adresse: [''],
    telephone: [''],
    email: ['', Validators.email],
    fuseau: ['Africa/Dakar'],
    devise: ['XOF'],
  });

  constructor() {
    effect(() => {
      if (this.droits.peutEcrire()) {
        this.formulaire.enable({ emitEvent: false });
      } else {
        this.formulaire.disable({ emitEvent: false });
      }
    });
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      this.appliquer(await this.service.lire());
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  private appliquer(e: EtablissementDto): void {
    this.etablissement.set(e);
    this.formulaire.patchValue({
      nom: e.nom ?? '',
      code: e.code ?? '',
      statut: e.statut ?? 'PUBLIC',
      ia: e.ia ?? '',
      ief: e.ief ?? '',
      adresse: e.adresse ?? '',
      telephone: e.telephone ?? '',
      email: e.email ?? '',
      fuseau: e.fuseau ?? 'Africa/Dakar',
      devise: e.devise ?? 'XOF',
    });
  }

  protected async enregistrer(): Promise<void> {
    const courant = this.etablissement();
    this.confirmation.set(null);
    if (!courant || this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    await this.executer(async () => {
      this.appliquer(
        await this.service.modifier(courant, this.formulaire.getRawValue()),
      );
      this.confirmation.set('Paramètres enregistrés.');
    });
  }

  protected async deposer(type: TypeImage, fichier: File): Promise<void> {
    await this.executer(async () => {
      this.etablissement.set(await this.service.deposerImage(type, fichier));
      this.confirmation.set('Image enregistrée.');
    });
  }

  protected async supprimer(type: TypeImage): Promise<void> {
    await this.executer(async () => {
      this.etablissement.set(await this.service.supprimerImage(type));
      this.confirmation.set('Image supprimée.');
    });
  }

  private async executer(action: () => Promise<void>): Promise<void> {
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      await action();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
