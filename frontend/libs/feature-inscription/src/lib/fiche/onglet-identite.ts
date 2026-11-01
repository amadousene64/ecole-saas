import { Component, inject, input, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  DonneesSensiblesDto,
  EleveDto,
  EtatDonneesSensiblesDto,
} from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceDossier } from '../services/service-dossier';
import { ServiceEleves } from '../services/service-eleves';
import { ChampMasque } from './champ-masque';

const CHAMPS = [
  { cle: 'sante', libelle: 'Santé' },
  { cle: 'allergies', libelle: 'Allergies' },
  { cle: 'situationFamiliale', libelle: 'Situation familiale' },
] as const;
type CleSensible = (typeof CHAMPS)[number]['cle'];

/**
 * Identité de l'élève (correction avec verrou de version) et données sensibles.
 * - DIRECTEUR et CENSEUR : masquées, bouton « Afficher » (consultation journalisée) ;
 * - SECRETARIAT : seulement « renseigné / non renseigné », et saisie sans relecture ;
 * - autres rôles : rien.
 */
@Component({
  selector: 'ecole-onglet-identite',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ChampMasque,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-identite">
        {{ message }}
      </p>
    }
    @if (enregistre()) {
      <p class="message ok" role="status">Identité enregistrée.</p>
    }
    <h2>Identité</h2>
    <form
      [formGroup]="formulaire"
      (ngSubmit)="enregistrer()"
      class="grille"
      data-testid="form-identite"
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
        <input matInput formControlName="lieuNaissance" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Nationalité (code pays)</mat-label>
        <input matInput formControlName="nationalite" maxlength="2" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Identifiant national</mat-label>
        <input matInput formControlName="identifiantNational" />
      </mat-form-field>
      @if (droits.peutEcrire()) {
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="enregistrer-identite"
          >
            Enregistrer
          </button>
        </div>
      }
    </form>

    @if (droits.peutLireSensible() || droits.voitEtatSensible()) {
      <h2 id="titre-sensibles">Données sensibles</h2>
      @if (droits.peutLireSensible()) {
        <p class="aide" data-testid="mention-journal">
          Ces données de santé et de situation familiale sont masquées. Chaque
          consultation est enregistrée dans le journal d'audit, avec votre nom.
        </p>
      }
      @if (erreurSensibles(); as message) {
        <p class="message erreur" role="alert">{{ message }}</p>
      }
      <section aria-labelledby="titre-sensibles" data-testid="bloc-sensibles">
        @if (droits.peutLireSensible()) {
          @for (c of champs; track c.cle) {
            <ecole-champ-masque
              [cle]="c.cle"
              [libelle]="c.libelle"
              [valeur]="affichees()?.[c.cle] ?? null"
              [peutAfficher]="true"
              (afficher)="afficherTout()"
            />
          }
          @if (affichees()) {
            <div class="actions">
              <button
                matButton
                type="button"
                (click)="masquer()"
                data-testid="masquer-sensibles"
              >
                Masquer
              </button>
            </div>
          }
        } @else {
          <ul class="etat" data-testid="etat-sensibles">
            @for (c of champs; track c.cle) {
              <li>
                {{ c.libelle }} :
                {{ renseigne(c.cle) ? 'renseigné' : 'non renseigné' }}
              </li>
            }
          </ul>
        }
        @if (droits.peutEcrire()) {
          <form
            [formGroup]="saisie"
            (ngSubmit)="saisir()"
            class="grille"
            data-testid="form-sensibles"
          >
            @for (c of champs; track c.cle) {
              <mat-form-field appearance="outline">
                <mat-label>{{ c.libelle }} (saisie)</mat-label>
                <textarea
                  matInput
                  rows="2"
                  [formControlName]="c.cle"
                  [attr.data-testid]="'saisie-' + c.cle"
                ></textarea>
                <mat-hint>Laisser vide pour ne rien changer.</mat-hint>
              </mat-form-field>
            }
            <div class="actions">
              <button
                matButton
                type="submit"
                [disabled]="occupe()"
                data-testid="enregistrer-sensibles"
              >
                Enregistrer les données sensibles
              </button>
            </div>
          </form>
        }
      </section>
    }
  `,
  styles: [STYLES_COMMUNS],
})
export class OngletIdentite {
  readonly eleve = input.required<EleveDto>();

  private readonly eleves = inject(ServiceEleves);
  private readonly dossier = inject(ServiceDossier);
  protected readonly droits = inject(Droits);

  protected readonly champs = CHAMPS;
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurSensibles = signal<string | null>(null);
  protected readonly enregistre = signal(false);
  protected readonly occupe = signal(false);
  /** Contenu lu, gardé en mémoire de ce composant seulement (jamais stocké). */
  protected readonly affichees = signal<DonneesSensiblesDto | null>(null);
  private readonly etat = signal<EtatDonneesSensiblesDto | null>(null);
  private versionSensibles: string | null = null;
  private courant: EleveDto | null = null;

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    prenoms: ['', [Validators.required, Validators.maxLength(100)]],
    nom: ['', [Validators.required, Validators.maxLength(100)]],
    sexe: ['F' as 'M' | 'F', Validators.required],
    dateNaissance: ['', Validators.required],
    lieuNaissance: ['', Validators.required],
    nationalite: ['SN', [Validators.required, Validators.maxLength(2)]],
    identifiantNational: [''],
  });

  protected readonly saisie = inject(NonNullableFormBuilder).group({
    sante: [''],
    allergies: [''],
    situationFamiliale: [''],
  });

  constructor() {
    queueMicrotask(() => this.initialiser());
  }

  private initialiser(): void {
    const e = this.eleve();
    this.courant = e;
    this.formulaire.reset({
      prenoms: e.prenoms ?? '',
      nom: e.nom ?? '',
      sexe: e.sexe ?? 'F',
      dateNaissance: e.dateNaissance ?? '',
      lieuNaissance: e.lieuNaissance ?? '',
      nationalite: e.nationalite ?? 'SN',
      identifiantNational: e.identifiantNational ?? '',
    });
    if (!this.droits.peutEcrire()) {
      this.formulaire.disable();
    }
    // Le secrétariat voit l'état ; direction et secrétariat en ont besoin pour la version à saisir.
    if (this.droits.peutEcrire() || this.droits.voitEtatSensible()) {
      void this.chargerEtat();
    }
  }

  protected renseigne(cle: CleSensible): boolean {
    const e = this.etat();
    if (!e) {
      return false;
    }
    return cle === 'sante'
      ? !!e.santeRenseignee
      : cle === 'allergies'
        ? !!e.allergiesRenseignees
        : !!e.situationFamilialeRenseignee;
  }

  private async chargerEtat(): Promise<void> {
    try {
      const { etat, version } = await this.dossier.etatSensibles(
        this.eleve().id ?? '',
      );
      this.etat.set(etat);
      this.versionSensibles = version;
    } catch (e) {
      this.erreurSensibles.set(messageErreur(e));
    }
  }

  protected async afficherTout(): Promise<void> {
    this.erreurSensibles.set(null);
    try {
      const { donnees, version } = await this.dossier.lireSensibles(
        this.eleve().id ?? '',
      );
      // Un champ vide reste affiché (« Non renseigné ») : null signifierait « masqué ».
      this.affichees.set({
        sante: donnees.sante ?? '',
        allergies: donnees.allergies ?? '',
        situationFamiliale: donnees.situationFamiliale ?? '',
      });
      this.versionSensibles = version;
    } catch (e) {
      this.erreurSensibles.set(messageErreur(e));
    }
  }

  protected masquer(): void {
    this.affichees.set(null);
  }

  protected async enregistrer(): Promise<void> {
    if (this.formulaire.invalid || !this.courant) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    this.erreur.set(null);
    this.enregistre.set(false);
    this.occupe.set(true);
    try {
      this.courant = await this.eleves.modifier(this.courant, {
        ...v,
        identifiantNational: v.identifiantNational || undefined,
      });
      this.enregistre.set(true);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async saisir(): Promise<void> {
    const v = this.saisie.getRawValue();
    // Champ vide : inchangé (absent de la requête) ; l'effacement n'est pas proposé ici.
    const corps = {
      ...(v.sante.trim() ? { sante: v.sante.trim() } : {}),
      ...(v.allergies.trim() ? { allergies: v.allergies.trim() } : {}),
      ...(v.situationFamiliale.trim()
        ? { situationFamiliale: v.situationFamiliale.trim() }
        : {}),
    };
    if (Object.keys(corps).length === 0) {
      return;
    }
    this.erreurSensibles.set(null);
    this.occupe.set(true);
    try {
      if (this.versionSensibles === null) {
        await this.chargerEtat();
      }
      const { etat, version } = await this.dossier.modifierSensibles(
        this.eleve().id ?? '',
        this.versionSensibles,
        corps,
      );
      this.etat.set(etat);
      this.versionSensibles = version;
      this.saisie.reset();
      // Le contenu affiché n'est plus à jour : on le remasque.
      this.affichees.set(null);
    } catch (e) {
      this.erreurSensibles.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
