import {
  Component,
  computed,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
import { MatSelectModule } from '@angular/material/select';
import {
  AffectationRequete,
  AnneeScolaireDto,
  ClasseDto,
  MatiereDto,
  MembreDto,
} from '@ecole/api-client';
import { Champ, Modale } from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
import { requis } from '../commun/requis';
import {
  libelleCycle,
  nomMembre,
  ServiceAffectations,
} from '../services/service-affectations';

type Type = AffectationRequete['type'];
type CodeCycle = NonNullable<AffectationRequete['cycles']>[number];

const CYCLES: readonly CodeCycle[] = ['ELEMENTAIRE', 'MOYEN', 'SECONDAIRE'];

let suivant = 0;

/**
 * Nouvelle affectation : une matière dans une classe ou une classe entière (élémentaire) pour un
 * enseignant, un ou plusieurs cycles pour un surveillant. Les listes ne proposent que ce que le
 * serveur accepte (rôle de la personne, cycle de la classe) ; il contrôle de nouveau tout.
 */
@Component({
  selector: 'ecole-modale-affectation',
  imports: [
    Champ,
    Modale,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
  ],
  template: `
    <ecole-modale
      titre="Nouvelle affectation"
      [sousTitre]="'Année ' + (annee().libelle ?? '')"
      testId="modale-affectation"
      largeur="large"
      [ouverte]="true"
      [modifiee]="formulaire.dirty"
      (fermee)="fermee.emit()"
    >
      @if (erreur(); as message) {
        <p class="message erreur" role="alert" data-testid="erreur-affectation">
          {{ message }}
        </p>
      }
      <form
        [id]="idFormulaire"
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="formulaire"
        data-testid="form-affectation"
      >
        <fieldset class="groupe-champs">
          <legend class="libelle">Affectation</legend>
          <mat-radio-group
            formControlName="type"
            class="champ-plein"
            data-testid="champ-type-affectation"
          >
            <mat-radio-button value="ENSEIGNEMENT"
              >Une matière dans une classe</mat-radio-button
            >
            <mat-radio-button value="TITULAIRE"
              >Maître titulaire (élémentaire)</mat-radio-button
            >
            <mat-radio-button value="SURVEILLANCE"
              >Surveillance de cycles</mat-radio-button
            >
          </mat-radio-group>
        </fieldset>
        <ecole-champ
          [libelle]="type() === 'SURVEILLANCE' ? 'Surveillant' : 'Enseignant'"
        >
          <mat-form-field appearance="outline">
            <mat-select
              formControlName="utilisateurId"
              data-testid="champ-personne"
            >
              @for (m of personnes(); track m.utilisateurId) {
                <mat-option [value]="m.utilisateurId">{{ nom(m) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </ecole-champ>
        @if (type() !== 'SURVEILLANCE') {
          <ecole-champ libelle="Classe">
            <mat-form-field appearance="outline">
              <mat-select
                formControlName="classeId"
                data-testid="champ-classe-affectation"
              >
                @for (c of classesProposees(); track c.id) {
                  <mat-option [value]="c.id">{{ c.nom }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </ecole-champ>
        }
        @if (type() === 'ENSEIGNEMENT') {
          <ecole-champ libelle="Matière">
            <mat-form-field appearance="outline">
              <mat-select
                formControlName="matiereId"
                data-testid="champ-matiere-affectation"
              >
                @for (m of matieresProposees(); track m.id) {
                  <mat-option [value]="m.id">{{ m.libelle }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </ecole-champ>
        }
        @if (type() === 'SURVEILLANCE') {
          <fieldset
            class="groupe-champs"
            data-testid="champ-cycles-affectation"
          >
            <legend class="libelle">Cycles surveillés</legend>
            @for (c of cycles; track c) {
              <mat-checkbox
                [checked]="cyclesChoisis().includes(c)"
                (change)="basculer(c, $event.checked)"
                [attr.data-testid]="'cycle-surveille-' + c"
              >
                {{ libelleCycle(c) }}
              </mat-checkbox>
            }
          </fieldset>
        }
        <ecole-champ libelle="Début" class="champ-court">
          <mat-form-field appearance="outline">
            <input
              matInput
              type="date"
              formControlName="debut"
              data-testid="champ-debut"
            />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Prend fin le" class="champ-court">
          <mat-form-field appearance="outline">
            <input
              matInput
              type="date"
              formControlName="fin"
              data-testid="champ-fin"
            />
            <mat-hint>Vide : jusqu'à nouvel ordre</mat-hint>
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
          data-testid="enregistrer-affectation"
        >
          Affecter
        </button>
      </div>
    </ecole-modale>
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class ModaleAffectation implements OnInit {
  private readonly service = inject(ServiceAffectations);

  readonly annee = input.required<AnneeScolaireDto>();
  readonly membres = input.required<MembreDto[]>();
  readonly classes = input.required<ClasseDto[]>();
  /** Cycle de chaque classe (par son niveau). */
  readonly cycleDeClasse = input.required<Record<string, string>>();
  readonly matieres = input.required<MatiereDto[]>();
  /** Date proposée pour le début (aujourd'hui, ou la rentrée si l'année n'a pas commencé). */
  readonly debutPropose = input.required<string>();
  readonly enregistree = output<void>();
  readonly fermee = output<void>();

  protected readonly cycles = CYCLES;
  protected readonly libelleCycle = libelleCycle;
  protected readonly nom = (m: MembreDto) => nomMembre(m);
  protected readonly idFormulaire = `form-affectation-${++suivant}`;
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly cyclesChoisis = signal<CodeCycle[]>([]);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    type: ['ENSEIGNEMENT' as Type],
    utilisateurId: ['', Validators.required],
    classeId: [''],
    matiereId: [''],
    debut: ['', Validators.required],
    fin: [''],
  });
  private readonly valeur = toSignal(this.formulaire.valueChanges, {
    initialValue: this.formulaire.getRawValue(),
  });
  protected readonly type = computed(
    () => this.valeur().type ?? 'ENSEIGNEMENT',
  );

  protected readonly personnes = computed(() => {
    const role = this.type() === 'SURVEILLANCE' ? 'SURVEILLANT' : 'ENSEIGNANT';
    return this.membres().filter((m) => m.roles?.includes(role));
  });
  protected readonly classesProposees = computed(() =>
    this.type() === 'TITULAIRE'
      ? this.classes().filter(
          (c) => this.cycleDeClasse()[c.id ?? ''] === 'ELEMENTAIRE',
        )
      : this.classes(),
  );
  protected readonly matieresProposees = computed(() => {
    const cycle = this.cycleDeClasse()[this.valeur().classeId ?? ''];
    return this.matieres().filter(
      (m) =>
        m.actif !== false &&
        (!cycle || (m.cycles as string[] | undefined)?.includes(cycle)),
    );
  });

  ngOnInit(): void {
    this.formulaire.controls.debut.setValue(this.debutPropose());
    this.formulaire.markAsPristine();
  }

  constructor() {
    this.formulaire.controls.type.valueChanges.subscribe(() =>
      this.formulaire.patchValue({
        utilisateurId: '',
        classeId: '',
        matiereId: '',
      }),
    );
  }

  protected basculer(c: CodeCycle, coche: boolean): void {
    this.formulaire.markAsDirty();
    this.cyclesChoisis.update((l) =>
      coche ? [...l, c] : l.filter((x) => x !== c),
    );
  }

  protected async enregistrer(): Promise<void> {
    const v = this.formulaire.getRawValue();
    const manque =
      this.formulaire.invalid ||
      (v.type !== 'SURVEILLANCE' && !v.classeId) ||
      (v.type === 'ENSEIGNEMENT' && !v.matiereId) ||
      (v.type === 'SURVEILLANCE' && this.cyclesChoisis().length === 0);
    if (manque) {
      this.formulaire.markAllAsTouched();
      this.erreur.set(
        'Complétez l’affectation : personne, classe ou cycles, et date de début.',
      );
      return;
    }
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      await this.service.creer({
        type: v.type,
        anneeId: requis(this.annee().id),
        utilisateurId: v.utilisateurId,
        classeId: v.type === 'SURVEILLANCE' ? undefined : v.classeId,
        matiereId: v.type === 'ENSEIGNEMENT' ? v.matiereId : undefined,
        cycles:
          v.type === 'SURVEILLANCE'
            ? CYCLES.filter((c) => this.cyclesChoisis().includes(c))
            : undefined,
        debut: v.debut,
        fin: v.fin || undefined,
      });
      this.enregistree.emit();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
