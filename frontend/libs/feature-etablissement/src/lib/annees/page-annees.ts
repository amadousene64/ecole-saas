import { Pastille, TonStatut, EnTetePage } from '@ecole/ui';
import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AnneeScolaireDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { LIBELLES_STATUT_ANNEE, StatutAnnee } from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceAnnees } from '../services/service-annees';
import { SectionCalendrier } from './section-calendrier';
import { SectionPeriodes } from './section-periodes';

type Action = 'ouvrir' | 'cloturer' | 'supprimer';

const TEXTES_CONFIRMATION: Record<Action, string> = {
  ouvrir:
    "Ouvrir l'année : les paramètres de notation des cycles et des niveaux sont alors copiés pour cette année et ne changeront plus. Une seule année peut être en cours.",
  cloturer:
    "Clôturer l'année : définitif. L'année et tout ce qui lui est rattaché passent en lecture seule.",
  supprimer: "Supprimer l'année en préparation et ses périodes.",
};

/** Années scolaires : création, ouverture, clôture, puis périodes et calendrier de l'année choisie. */
@Component({
  selector: 'ecole-page-annees',
  imports: [
    EnTetePage,
    DatePipe,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    SectionPeriodes,
    SectionCalendrier,
    Pastille,
  ],
  template: `
    <ecole-en-tete-page
      titre="Années scolaires et périodes"
      sousTitre="Années, périodes et calendrier"
    />
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-annees">
        {{ message }}
      </p>
    }
    @if (confirmation(); as message) {
      <p class="message ok" role="status" data-testid="confirmation-annees">
        {{ message }}
      </p>
    }

    <table data-testid="table-annees" class="cartes">
      <thead>
        <tr>
          <th>Année</th>
          <th>Début</th>
          <th>Fin</th>
          <th>Statut</th>
          <th><span class="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        @for (a of annees(); track a.id) {
          <tr
            [attr.data-testid]="'annee-' + a.libelle"
            [attr.aria-selected]="a.id === selectionId()"
          >
            <td data-label="Année">
              <button
                matButton
                type="button"
                (click)="selectionner(a)"
                [attr.data-testid]="'choisir-' + a.libelle"
              >
                {{ a.libelle }}
              </button>
            </td>
            <td data-label="Début">{{ a.dateDebut | date: 'dd/MM/yyyy' }}</td>
            <td data-label="Fin">{{ a.dateFin | date: 'dd/MM/yyyy' }}</td>
            <td data-label="Statut">
              <ecole-pastille [ton]="tonStatut(a.statut)">
                {{ libelleStatut(a.statut) }}
              </ecole-pastille>
            </td>
            <td data-label="Actions">
              @if (droits.estDirecteur() && a.statut === 'PREPARATION') {
                <button
                  matButton
                  type="button"
                  (click)="demander('ouvrir', a)"
                  data-testid="ouvrir"
                >
                  Ouvrir
                </button>
              }
              @if (droits.estDirecteur() && a.statut === 'EN_COURS') {
                <button
                  matButton
                  type="button"
                  (click)="demander('cloturer', a)"
                  data-testid="cloturer"
                >
                  Clôturer
                </button>
              }
              @if (droits.peutEcrire() && a.statut === 'PREPARATION') {
                <button
                  matButton
                  type="button"
                  (click)="demander('supprimer', a)"
                  data-testid="supprimer-annee"
                >
                  Supprimer
                </button>
              }
            </td>
          </tr>
        } @empty {
          <tr>
            <td colspan="5" class="aide">Aucune année scolaire.</td>
          </tr>
        }
      </tbody>
    </table>

    @if (enAttente(); as attente) {
      <div
        class="message erreur"
        role="alertdialog"
        aria-label="Confirmation"
        data-testid="confirmer"
      >
        <p>
          <strong>{{ attente.annee.libelle }}</strong> :
          {{ texteConfirmation() }}
        </p>
        <button
          matButton="filled"
          type="button"
          (click)="confirmer()"
          data-testid="confirmer-oui"
        >
          Confirmer
        </button>
        <button matButton type="button" (click)="enAttente.set(null)">
          Annuler
        </button>
      </div>
    }

    @if (droits.peutEcrire()) {
      <h2>Nouvelle année</h2>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="creer()"
        class="grille"
        data-testid="form-annee"
      >
        <mat-form-field appearance="outline">
          <mat-label>Année (AAAA-AAAA)</mat-label>
          <input
            matInput
            formControlName="libelle"
            placeholder="2027-2028"
            data-testid="champ-annee-libelle"
          />
          @if (formulaire.controls.libelle.hasError('pattern')) {
            <mat-error>Format AAAA-AAAA, par exemple 2027-2028.</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Début</mat-label>
          <input
            matInput
            type="date"
            formControlName="dateDebut"
            data-testid="champ-annee-debut"
          />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Fin</mat-label>
          <input
            matInput
            type="date"
            formControlName="dateFin"
            data-testid="champ-annee-fin"
          />
        </mat-form-field>
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="creer-annee"
          >
            Créer l'année
          </button>
        </div>
      </form>
      <p class="aide">
        La création génère les périodes de chaque cycle (trimestres ou
        semestres) et propose les jours fériés fixes et ceux calculés depuis
        Pâques.
      </p>
    }

    @if (selection(); as annee) {
      <h2 data-testid="detail-titre">Année {{ annee.libelle }}</h2>
      @if (annee.statut === 'CLOTUREE') {
        <p class="aide" data-testid="annee-cloturee">
          Année clôturée : lecture seule.
        </p>
      }
      <ecole-section-periodes [annee]="annee" />
      <ecole-section-calendrier [annee]="annee" />
    }
  `,
  styles: [
    STYLES_COMMUNS,
    `
      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip-path: inset(50%);
      }
    `,
  ],
})
export class PageAnnees {
  private readonly service = inject(ServiceAnnees);
  protected readonly droits = inject(Droits);

  protected readonly annees = signal<AnneeScolaireDto[]>([]);
  protected readonly selectionId = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly confirmation = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly enAttente = signal<{
    action: Action;
    annee: AnneeScolaireDto;
  } | null>(null);

  protected readonly selection = computed(
    () => this.annees().find((a) => a.id === this.selectionId()) ?? null,
  );
  protected readonly texteConfirmation = computed(() => {
    const attente = this.enAttente();
    return attente ? TEXTES_CONFIRMATION[attente.action] : '';
  });

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    libelle: ['', [Validators.required, Validators.pattern(/^\d{4}-\d{4}$/)]],
    dateDebut: ['', Validators.required],
    dateFin: ['', Validators.required],
  });

  constructor() {
    void this.charger();
  }

  /** En cours : vert ; en préparation et clôturée : neutre (le texte porte le sens). */
  protected tonStatut(statut: string | undefined): TonStatut {
    return statut === 'EN_COURS' ? 'primaire' : 'neutre';
  }

  protected libelleStatut(statut: string | undefined): string {
    return LIBELLES_STATUT_ANNEE[statut as StatutAnnee] ?? statut ?? '';
  }

  private async charger(choisir?: string): Promise<void> {
    try {
      const annees = await this.service.annees();
      this.annees.set(annees);
      const voulue =
        choisir ??
        this.selectionId() ??
        (annees.find((a) => a.statut === 'EN_COURS') ?? annees[0])?.id ??
        null;
      this.selectionId.set(voulue);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected selectionner(annee: AnneeScolaireDto): void {
    this.selectionId.set(annee.id ?? null);
  }

  protected demander(action: Action, annee: AnneeScolaireDto): void {
    this.confirmation.set(null);
    this.enAttente.set({ action, annee });
  }

  protected async confirmer(): Promise<void> {
    const attente = this.enAttente();
    if (!attente) {
      return;
    }
    this.enAttente.set(null);
    await this.executer(async () => {
      const { action, annee } = attente;
      if (action === 'ouvrir') {
        await this.service.ouvrir(annee);
        this.confirmation.set(`Année ${annee.libelle} ouverte.`);
      } else if (action === 'cloturer') {
        await this.service.cloturer(annee);
        this.confirmation.set(`Année ${annee.libelle} clôturée.`);
      } else {
        await this.service.supprimer(annee);
        this.confirmation.set(`Année ${annee.libelle} supprimée.`);
        this.selectionId.set(null);
      }
      await this.charger(action === 'supprimer' ? undefined : annee.id);
    });
  }

  protected async creer(): Promise<void> {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    await this.executer(async () => {
      const creee = await this.service.creer(this.formulaire.getRawValue());
      this.formulaire.reset();
      this.confirmation.set(`Année ${creee.libelle} créée.`);
      await this.charger(creee.id);
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
