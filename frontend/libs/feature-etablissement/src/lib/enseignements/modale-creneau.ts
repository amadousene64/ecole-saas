import {
  Component,
  computed,
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
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { CreneauDto, CreneauRequete } from '@ecole/api-client';
import { Champ, Modale } from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
import { ServiceEnseignements } from '../services/service-enseignements';

export type Jour = CreneauRequete['jours'][number];

/** Jours de la semaine, du lundi au dimanche. */
export const JOURS: readonly { code: Jour; court: string; long: string }[] = [
  { code: 'MONDAY', court: 'Lun', long: 'lundi' },
  { code: 'TUESDAY', court: 'Mar', long: 'mardi' },
  { code: 'WEDNESDAY', court: 'Mer', long: 'mercredi' },
  { code: 'THURSDAY', court: 'Jeu', long: 'jeudi' },
  { code: 'FRIDAY', court: 'Ven', long: 'vendredi' },
  { code: 'SATURDAY', court: 'Sam', long: 'samedi' },
  { code: 'SUNDAY', court: 'Dim', long: 'dimanche' },
];

const SEMAINE: readonly Jour[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
];

let suivant = 0;

/**
 * Ajout ou modification d'un créneau de la grille d'un cycle : heures, jours, cours ou pause.
 * Le serveur refuse un chevauchement avec un autre créneau du cycle un même jour.
 */
@Component({
  selector: 'ecole-modale-creneau',
  imports: [
    Champ,
    Modale,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
  ],
  template: `
    <ecole-modale
      [titre]="creneau() ? 'Modifier le créneau' : 'Ajouter un créneau'"
      [sousTitre]="'Grille horaire · ' + libelleCycle()"
      testId="modale-creneau"
      focusInitial="[data-testid=champ-creneau-debut]"
      [ouverte]="true"
      [modifiee]="modifiee()"
      (fermee)="fermee.emit()"
    >
      @if (erreur(); as message) {
        <p class="message erreur" role="alert" data-testid="erreur-creneau">
          {{ message }}
        </p>
      }
      <form
        [id]="idFormulaire"
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="formulaire"
        data-testid="form-creneau"
      >
        <ecole-champ libelle="Début" class="champ-court">
          <mat-form-field appearance="outline">
            <input
              matInput
              type="time"
              min="06:00"
              max="20:00"
              formControlName="debut"
              data-testid="champ-creneau-debut"
            />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Fin" class="champ-court">
          <mat-form-field appearance="outline">
            <input
              matInput
              type="time"
              min="06:00"
              max="20:00"
              formControlName="fin"
              data-testid="champ-creneau-fin"
            />
          </mat-form-field>
        </ecole-champ>
        <fieldset class="groupe-champs" data-testid="champ-creneau-jours">
          <legend class="libelle">Jours</legend>
          @for (j of jours; track j.code) {
            <mat-checkbox
              [checked]="choisis().includes(j.code)"
              (change)="basculer(j.code, $event.checked)"
              [attr.data-testid]="'jour-' + j.code"
            >
              {{ j.long }}
            </mat-checkbox>
          }
          @if (aucunJour()) {
            <p class="message erreur" role="alert">
              Choisissez au moins un jour.
            </p>
          }
        </fieldset>
        <fieldset class="groupe-champs">
          <legend class="libelle">Type</legend>
          <mat-radio-group
            formControlName="type"
            class="champ-plein"
            data-testid="champ-creneau-type"
          >
            <mat-radio-button value="COURS">Cours (appel)</mat-radio-button>
            <mat-radio-button value="PAUSE"
              >Pause (récréation, déjeuner)</mat-radio-button
            >
          </mat-radio-group>
        </fieldset>
        <ecole-champ libelle="Libellé">
          <mat-form-field appearance="outline">
            <input
              matInput
              formControlName="libelle"
              data-testid="champ-creneau-libelle"
            />
            <mat-hint>M1, Récréation…</mat-hint>
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
          data-testid="enregistrer-creneau"
        >
          {{ creneau() ? 'Enregistrer' : 'Ajouter' }}
        </button>
      </div>
    </ecole-modale>
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class ModaleCreneau implements OnInit {
  private readonly service = inject(ServiceEnseignements);

  readonly cycleId = input.required<string>();
  readonly libelleCycle = input.required<string>();
  /** Créneau modifié ; absent pour un ajout. */
  readonly creneau = input<CreneauDto | null>(null);
  readonly enregistre = output<CreneauDto>();
  readonly fermee = output<void>();

  protected readonly jours = JOURS;
  protected readonly idFormulaire = `form-creneau-${++suivant}`;
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly choisis = signal<Jour[]>([...SEMAINE]);
  protected readonly modifiee = signal(false);
  private readonly tente = signal(false);
  protected readonly aucunJour = computed(
    () => this.tente() && this.choisis().length === 0,
  );

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    debut: ['', Validators.required],
    fin: ['', Validators.required],
    type: ['COURS' as 'COURS' | 'PAUSE'],
    libelle: ['', Validators.maxLength(30)],
  });

  constructor() {
    this.formulaire.valueChanges.subscribe(() =>
      this.modifiee.set(this.formulaire.dirty),
    );
  }

  ngOnInit(): void {
    const c = this.creneau();
    if (c) {
      this.formulaire.reset({
        debut: heure(c.debut),
        fin: heure(c.fin),
        type: c.type ?? 'COURS',
        libelle: c.libelle ?? '',
      });
      this.choisis.set([...(c.jours ?? [])]);
    }
  }

  protected basculer(jour: Jour, coche: boolean): void {
    this.modifiee.set(true);
    this.choisis.update((liste) =>
      coche ? [...liste, jour] : liste.filter((j) => j !== jour),
    );
  }

  protected async enregistrer(): Promise<void> {
    this.tente.set(true);
    if (this.formulaire.invalid || this.choisis().length === 0) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    const corps: CreneauRequete = {
      debut: v.debut,
      fin: v.fin,
      type: v.type,
      libelle: v.libelle || undefined,
      jours: JOURS.map((j) => j.code).filter((j) => this.choisis().includes(j)),
    };
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const c = this.creneau();
      this.enregistre.emit(
        c
          ? await this.service.modifierCreneau(c, corps)
          : await this.service.creerCreneau(this.cycleId(), corps),
      );
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}

/** « 08:00:00 » → « 08:00 ». */
export function heure(valeur: string | undefined): string {
  return (valeur ?? '').slice(0, 5);
}

/** « du lundi au vendredi », ou « lun, mer, sam ». */
export function libelleJours(jours: readonly Jour[] | undefined): string {
  const liste = JOURS.filter((j) => jours?.includes(j.code));
  const indices = liste.map((j) => JOURS.indexOf(j));
  const consecutifs =
    liste.length >= 3 &&
    indices.every((x, i) => i === 0 || x === indices[i - 1] + 1);
  if (consecutifs) {
    return `du ${liste[0].long} au ${liste[liste.length - 1].long}`;
  }
  return liste.map((j) => j.long).join(', ');
}
