import { Component, computed, effect, inject, signal } from '@angular/core';
import { requis } from '../commun/requis';
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
  AnneeScolaireDto,
  ClasseDto,
  NiveauDto,
  SalleDto,
  SerieDto,
} from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceAnnees } from '../services/service-annees';
import { ServiceClasses } from '../services/service-classes';
import { ServiceStructure } from '../services/service-structure';

/**
 * Classes d'une année. La série n'apparaît (et n'est obligatoire) que si le niveau en propose ;
 * le niveau d'une classe ne change plus dès que l'année est en cours ; une année clôturée est en
 * lecture seule.
 */
@Component({
  selector: 'ecole-section-classes',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-classes">
        {{ message }}
      </p>
    }
    <div class="grille">
      <mat-form-field appearance="outline">
        <mat-label>Année scolaire</mat-label>
        <mat-select
          [value]="anneeId()"
          (selectionChange)="changerAnnee($event.value)"
          data-testid="filtre-annee"
        >
          @for (a of annees(); track a.id) {
            <mat-option [value]="a.id">{{ a.libelle }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>
    @if (anneeCourante()?.statut === 'CLOTUREE') {
      <p class="aide" data-testid="classes-cloturee">
        Année clôturée : lecture seule.
      </p>
    }

    <table data-testid="table-classes" class="cartes">
      <thead>
        <tr>
          <th>Classe</th>
          <th>Niveau</th>
          <th>Série</th>
          <th>Capacité</th>
          <th>Salle</th>
          @if (modifiable()) {
            <th></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (c of classes(); track c.id) {
          <tr [attr.data-testid]="'classe-' + c.nom">
            <td data-label="Classe">{{ c.nom }}</td>
            <td data-label="Niveau">{{ nomNiveau(c.niveauId) }}</td>
            <td data-label="Série">{{ codeSerie(c.serieId) }}</td>
            <td data-label="Capacité">{{ c.capacite }}</td>
            <td data-label="Salle">{{ nomSalle(c.salleId) }}</td>
            @if (modifiable()) {
              <td data-label="Actions">
                <button matButton type="button" (click)="modifier(c)">
                  Modifier
                </button>
                <button matButton type="button" (click)="supprimer(c)">
                  Supprimer
                </button>
              </td>
            }
          </tr>
        } @empty {
          <tr>
            <td colspan="6" class="aide">Aucune classe pour cette année.</td>
          </tr>
        }
      </tbody>
    </table>

    @if (modifiable()) {
      <h2>
        {{
          edition() ? 'Modifier la classe ' + edition()!.nom : 'Nouvelle classe'
        }}
      </h2>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="grille"
        data-testid="form-classe"
      >
        <mat-form-field appearance="outline">
          <mat-label>Nom (ex. TS2B)</mat-label>
          <input
            matInput
            formControlName="nom"
            data-testid="champ-classe-nom"
          />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Niveau</mat-label>
          <mat-select
            formControlName="niveauId"
            data-testid="champ-classe-niveau"
          >
            @for (n of niveauxActifs(); track n.id) {
              <mat-option [value]="n.id">{{ n.libelle }}</mat-option>
            }
          </mat-select>
          @if (niveauFige()) {
            <mat-hint
              >Le niveau et la série ne changent plus une fois l'année en
              cours.</mat-hint
            >
          }
        </mat-form-field>
        @if (seriesDuNiveau().length > 0) {
          <mat-form-field appearance="outline">
            <mat-label>Série</mat-label>
            <mat-select
              formControlName="serieId"
              required
              data-testid="champ-classe-serie"
            >
              @for (s of seriesDuNiveau(); track s.id) {
                <mat-option [value]="s.id">{{ s.code }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Capacité</mat-label>
          <input
            matInput
            type="number"
            min="1"
            max="500"
            formControlName="capacite"
            data-testid="champ-classe-capacite"
          />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Salle</mat-label>
          <mat-select
            formControlName="salleId"
            data-testid="champ-classe-salle"
          >
            <mat-option value="">Aucune</mat-option>
            @for (s of salles(); track s.id) {
              <mat-option [value]="s.id">{{ s.nom }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe() || !formulaireValide()"
            data-testid="enregistrer-classe"
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
export class SectionClasses {
  private readonly service = inject(ServiceClasses);
  private readonly serviceAnnees = inject(ServiceAnnees);
  private readonly structure = inject(ServiceStructure);
  protected readonly droits = inject(Droits);

  protected readonly annees = signal<AnneeScolaireDto[]>([]);
  protected readonly anneeId = signal<string | undefined>(undefined);
  protected readonly classes = signal<ClasseDto[]>([]);
  protected readonly niveaux = signal<NiveauDto[]>([]);
  protected readonly series = signal<SerieDto[]>([]);
  protected readonly salles = signal<SalleDto[]>([]);
  protected readonly edition = signal<ClasseDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    nom: ['', [Validators.required, Validators.maxLength(30)]],
    niveauId: ['', Validators.required],
    serieId: [''],
    capacite: [
      40,
      [Validators.required, Validators.min(1), Validators.max(500)],
    ],
    salleId: [''],
  });
  private readonly niveauChoisi = signal('');
  private readonly serieChoisie = signal('');

  protected readonly anneeCourante = computed(
    () => this.annees().find((a) => a.id === this.anneeId()) ?? null,
  );
  protected readonly modifiable = computed(
    () =>
      this.droits.peutEcrire() &&
      (this.anneeCourante()?.statut ?? 'CLOTUREE') !== 'CLOTUREE',
  );
  protected readonly niveauxActifs = computed(() =>
    this.niveaux().filter((n) => n.actif || n.id === this.edition()?.niveauId),
  );
  protected readonly seriesDuNiveau = computed(() => {
    const niveau = this.niveaux().find((n) => n.id === this.niveauChoisi());
    return this.series().filter((s) => niveau?.serieIds?.includes(s.id ?? ''));
  });
  /** Niveau et série d'une classe existante ne changent plus en année EN_COURS (règle du serveur). */
  protected readonly niveauFige = computed(
    () => !!this.edition() && this.anneeCourante()?.statut === 'EN_COURS',
  );
  protected readonly formulaireValide = computed(
    () =>
      this.niveauChoisi() !== '' &&
      (this.seriesDuNiveau().length === 0 || this.serieChoisie() !== ''),
  );

  constructor() {
    this.formulaire.controls.niveauId.valueChanges.subscribe((v) => {
      this.niveauChoisi.set(v);
      this.formulaire.controls.serieId.reset('');
    });
    this.formulaire.controls.serieId.valueChanges.subscribe((v) =>
      this.serieChoisie.set(v),
    );
    effect(() => {
      if (this.niveauFige()) {
        this.formulaire.controls.niveauId.disable({ emitEvent: false });
        this.formulaire.controls.serieId.disable({ emitEvent: false });
      } else {
        this.formulaire.controls.niveauId.enable({ emitEvent: false });
        this.formulaire.controls.serieId.enable({ emitEvent: false });
      }
    });
    void this.charger();
  }

  protected nomNiveau(id: string | undefined): string {
    return this.niveaux().find((n) => n.id === id)?.libelle ?? '';
  }

  protected codeSerie(id: string | undefined): string {
    return this.series().find((s) => s.id === id)?.code ?? '—';
  }

  protected nomSalle(id: string | undefined): string {
    return this.salles().find((s) => s.id === id)?.nom ?? '—';
  }

  private async charger(): Promise<void> {
    try {
      const [annees, niveaux, series, salles] = await Promise.all([
        this.serviceAnnees.annees(),
        this.structure.niveaux(),
        this.structure.series(),
        this.service.salles(),
      ]);
      this.annees.set(annees);
      this.niveaux.set(niveaux);
      this.series.set(series);
      this.salles.set(salles);
      const initiale = (
        annees.find((a) => a.statut === 'EN_COURS') ?? annees[0]
      )?.id;
      this.anneeId.set(initiale);
      await this.chargerClasses();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  private async chargerClasses(): Promise<void> {
    const annee = this.anneeId();
    this.classes.set(annee ? await this.service.classes(annee) : []);
  }

  protected async changerAnnee(id: string): Promise<void> {
    this.anneeId.set(id);
    this.annuler();
    try {
      await this.chargerClasses();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected modifier(classe: ClasseDto): void {
    this.edition.set(classe);
    this.niveauChoisi.set(classe.niveauId ?? '');
    this.serieChoisie.set(classe.serieId ?? '');
    this.formulaire.reset({
      nom: classe.nom ?? '',
      niveauId: classe.niveauId ?? '',
      serieId: classe.serieId ?? '',
      capacite: classe.capacite ?? 40,
      salleId: classe.salleId ?? '',
    });
    this.niveauChoisi.set(classe.niveauId ?? '');
    this.serieChoisie.set(classe.serieId ?? '');
  }

  protected annuler(): void {
    this.edition.set(null);
    this.niveauChoisi.set('');
    this.serieChoisie.set('');
    this.formulaire.reset({
      nom: '',
      niveauId: '',
      serieId: '',
      capacite: 40,
      salleId: '',
    });
  }

  protected async enregistrer(): Promise<void> {
    if (this.formulaire.invalid || !this.formulaireValide()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    const corps = {
      anneeId: this.anneeId(),
      niveauId: this.edition()?.niveauId ?? v.niveauId,
      serieId: v.serieId || undefined,
      nom: v.nom,
      capacite: v.capacite,
      salleId: v.salleId || undefined,
    };
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const courante = this.edition();
      if (courante) {
        await this.service.modifierClasse(courante, {
          ...corps,
          niveauId: this.niveauFige() ? requis(courante.niveauId) : v.niveauId,
        });
      } else {
        await this.service.creerClasse(corps);
      }
      this.annuler();
      await this.chargerClasses();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async supprimer(classe: ClasseDto): Promise<void> {
    this.erreur.set(null);
    try {
      await this.service.supprimerClasse(classe);
      await this.chargerClasses();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }
}
