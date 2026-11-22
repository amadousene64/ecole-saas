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
import { MatiereDto, MatiereRequete } from '@ecole/api-client';
import { Champ, Modale } from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
import { ServiceEnseignements } from '../services/service-enseignements';

export type CodeCycle = MatiereRequete['cycles'][number];

/** Les trois cycles, dans l'ordre du référentiel. */
export const CYCLES: readonly { code: CodeCycle; libelle: string }[] = [
  { code: 'ELEMENTAIRE', libelle: 'Élémentaire' },
  { code: 'MOYEN', libelle: 'Moyen' },
  { code: 'SECONDAIRE', libelle: 'Secondaire' },
];

let suivant = 0;

/**
 * Ajout ou modification d'une matière : code, libellé, groupe d'affichage, cycles, ordre. Le
 * parent crée la modale à l'ouverture et la détruit à la fermeture (`@if`).
 */
@Component({
  selector: 'ecole-modale-matiere',
  imports: [
    Champ,
    Modale,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <ecole-modale
      [titre]="matiere() ? 'Modifier la matière' : 'Ajouter une matière'"
      sousTitre="Les coefficients par niveau et série viendront avec les notes."
      testId="modale-matiere"
      focusInitial="[data-testid=champ-matiere-code]"
      [ouverte]="true"
      [modifiee]="modifiee()"
      (fermee)="fermee.emit()"
    >
      @if (erreur(); as message) {
        <p class="message erreur" role="alert" data-testid="erreur-matiere">
          {{ message }}
        </p>
      }
      <form
        [id]="idFormulaire"
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="formulaire"
        data-testid="form-matiere"
      >
        <ecole-champ libelle="Code">
          <mat-form-field appearance="outline">
            <input
              matInput
              formControlName="code"
              autocomplete="off"
              data-testid="champ-matiere-code"
            />
            <mat-hint>Lettres et chiffres, 10 au plus (MATHS, SVT…)</mat-hint>
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Libellé">
          <mat-form-field appearance="outline">
            <input
              matInput
              formControlName="libelle"
              data-testid="champ-matiere-libelle"
            />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Groupe">
          <mat-form-field appearance="outline">
            <input
              matInput
              formControlName="groupe"
              data-testid="champ-matiere-groupe"
            />
            <mat-hint>Domaine ou discipline mère (Français…)</mat-hint>
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Ordre d'affichage" class="champ-court">
          <mat-form-field appearance="outline">
            <input
              matInput
              type="number"
              min="0"
              max="999"
              formControlName="ordre"
              data-testid="champ-matiere-ordre"
            />
          </mat-form-field>
        </ecole-champ>
        <fieldset class="groupe-champs" data-testid="champ-matiere-cycles">
          <legend class="libelle">Cycles où elle est enseignée</legend>
          @for (c of cycles; track c.code) {
            <mat-checkbox
              [checked]="choisis().includes(c.code)"
              (change)="basculer(c.code, $event.checked)"
              [attr.data-testid]="'cycle-' + c.code"
            >
              {{ c.libelle }}
            </mat-checkbox>
          }
          @if (aucunCycle()) {
            <p class="message erreur" role="alert">
              Choisissez au moins un cycle.
            </p>
          }
        </fieldset>
        @if (matiere()) {
          <mat-checkbox
            formControlName="actif"
            data-testid="champ-matiere-active"
          >
            Matière enseignée
          </mat-checkbox>
        }
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
          data-testid="enregistrer-matiere"
        >
          {{ matiere() ? 'Enregistrer' : 'Ajouter' }}
        </button>
      </div>
    </ecole-modale>
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class ModaleMatiere implements OnInit {
  private readonly service = inject(ServiceEnseignements);

  /** Matière modifiée ; absente pour un ajout. */
  readonly matiere = input<MatiereDto | null>(null);
  readonly enregistree = output<MatiereDto>();
  readonly fermee = output<void>();

  protected readonly cycles = CYCLES;
  protected readonly idFormulaire = `form-matiere-${++suivant}`;
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly choisis = signal<CodeCycle[]>([]);
  /** Une saisie en cours : fermer demande confirmation. */
  protected readonly modifiee = signal(false);
  private readonly tente = signal(false);
  protected readonly aucunCycle = computed(
    () => this.tente() && this.choisis().length === 0,
  );

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    code: ['', [Validators.required, Validators.maxLength(10)]],
    libelle: ['', [Validators.required, Validators.maxLength(80)]],
    groupe: ['', Validators.maxLength(60)],
    ordre: [500, [Validators.required, Validators.min(0), Validators.max(999)]],
    actif: [true],
  });

  constructor() {
    this.formulaire.valueChanges.subscribe(() =>
      this.modifiee.set(this.formulaire.dirty),
    );
  }

  ngOnInit(): void {
    const m = this.matiere();
    if (m) {
      this.formulaire.reset({
        code: m.code ?? '',
        libelle: m.libelle ?? '',
        groupe: m.groupe ?? '',
        ordre: m.ordre ?? 500,
        actif: m.actif ?? true,
      });
      this.choisis.set([...(m.cycles ?? [])]);
    }
  }

  protected basculer(code: CodeCycle, coche: boolean): void {
    this.modifiee.set(true);
    this.choisis.update((liste) =>
      coche ? [...liste, code] : liste.filter((c) => c !== code),
    );
  }

  protected async enregistrer(): Promise<void> {
    this.tente.set(true);
    if (this.formulaire.invalid || this.choisis().length === 0) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    const corps: MatiereRequete = {
      code: v.code,
      libelle: v.libelle,
      groupe: v.groupe || undefined,
      ordre: v.ordre,
      cycles: CYCLES.map((c) => c.code).filter((c) =>
        this.choisis().includes(c),
      ),
      actif: v.actif,
    };
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const m = this.matiere();
      this.enregistree.emit(
        m
          ? await this.service.modifierMatiere(m, corps)
          : await this.service.creerMatiere(corps),
      );
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
