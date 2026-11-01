import { DatePipe } from '@angular/common';
import { requis } from '../commun/requis';
import {
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AnneeScolaireDto, EvenementDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { TypeEvenement, TYPES_EVENEMENT } from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceAnnees } from '../services/service-annees';

/** Jours fériés et vacances d'une année. Les fériés proposés à la création restent modifiables. */
@Component({
  selector: 'ecole-section-calendrier',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  template: `
    <h3>Calendrier</h3>
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-calendrier">
        {{ message }}
      </p>
    }
    <table data-testid="table-calendrier" class="cartes">
      <thead>
        <tr>
          <th>Type</th>
          <th>Libellé</th>
          <th>Du</th>
          <th>Au</th>
          @if (modifiable()) {
            <th></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (e of evenements(); track e.id) {
          <tr>
            <td data-label="Type">
              {{ e.type === 'FERIE' ? 'Jour férié' : 'Vacances' }}
            </td>
            <td data-label="Libellé">{{ e.libelle }}</td>
            <td data-label="Du">{{ e.dateDebut | date: 'dd/MM/yyyy' }}</td>
            <td data-label="Au">{{ e.dateFin | date: 'dd/MM/yyyy' }}</td>
            @if (modifiable()) {
              <td data-label="Actions">
                <button matButton type="button" (click)="modifier(e)">
                  Modifier
                </button>
                <button matButton type="button" (click)="supprimer(e)">
                  Supprimer
                </button>
              </td>
            }
          </tr>
        } @empty {
          <tr>
            <td colspan="5" class="aide">Aucun événement.</td>
          </tr>
        }
      </tbody>
    </table>

    @if (modifiable()) {
      <h4>{{ edition() ? "Modifier l'événement" : 'Ajouter un événement' }}</h4>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="grille"
        data-testid="form-evenement"
      >
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select formControlName="type" data-testid="champ-evenement-type">
            @for (t of types; track t.valeur) {
              <mat-option [value]="t.valeur">{{ t.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Libellé</mat-label>
          <input
            matInput
            formControlName="libelle"
            data-testid="champ-evenement-libelle"
          />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Du</mat-label>
          <input
            matInput
            type="date"
            formControlName="dateDebut"
            data-testid="champ-evenement-debut"
          />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Au</mat-label>
          <input
            matInput
            type="date"
            formControlName="dateFin"
            data-testid="champ-evenement-fin"
          />
        </mat-form-field>
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="enregistrer-evenement"
          >
            {{ edition() ? 'Enregistrer' : 'Ajouter' }}
          </button>
          @if (edition()) {
            <button matButton type="button" (click)="annuler()">Annuler</button>
          }
        </div>
      </form>
      <p class="aide">
        Les fêtes musulmanes dépendent de l'observation de la lune : ajoutez-les
        à la main.
      </p>
    }
  `,
  styles: [STYLES_COMMUNS],
})
export class SectionCalendrier {
  private readonly service = inject(ServiceAnnees);
  protected readonly droits = inject(Droits);
  protected readonly types = TYPES_EVENEMENT;

  readonly annee = input.required<AnneeScolaireDto>();

  protected readonly evenements = signal<EvenementDto[]>([]);
  protected readonly edition = signal<EvenementDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);

  protected readonly modifiable = computed(
    () => this.droits.peutEcrire() && this.annee().statut !== 'CLOTUREE',
  );

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    type: ['FERIE' as TypeEvenement],
    libelle: ['', [Validators.required, Validators.maxLength(120)]],
    dateDebut: ['', Validators.required],
    dateFin: ['', Validators.required],
  });

  constructor() {
    effect(() => {
      void this.charger(requis(this.annee().id));
    });
  }

  private async charger(anneeId: string): Promise<void> {
    try {
      this.evenements.set(await this.service.calendrier(anneeId));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected modifier(evenement: EvenementDto): void {
    this.edition.set(evenement);
    this.formulaire.reset({
      type: evenement.type ?? 'FERIE',
      libelle: evenement.libelle ?? '',
      dateDebut: evenement.dateDebut ?? '',
      dateFin: evenement.dateFin ?? '',
    });
  }

  protected annuler(): void {
    this.edition.set(null);
    this.formulaire.reset({
      type: 'FERIE',
      libelle: '',
      dateDebut: '',
      dateFin: '',
    });
  }

  protected async enregistrer(): Promise<void> {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const corps = this.formulaire.getRawValue();
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const courant = this.edition();
      if (courant) {
        await this.service.modifierEvenement(courant, corps);
      } else {
        await this.service.ajouterEvenement(requis(this.annee().id), corps);
      }
      this.annuler();
      await this.charger(requis(this.annee().id));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async supprimer(evenement: EvenementDto): Promise<void> {
    this.erreur.set(null);
    try {
      await this.service.supprimerEvenement(evenement);
      await this.charger(requis(this.annee().id));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }
}
