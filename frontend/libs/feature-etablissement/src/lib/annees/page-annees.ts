import {
  CHAMPS_FORMULAIRE,
  Confirmation,
  EnTetePage,
  Pastille,
  TonStatut,
} from '@ecole/ui';
import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { AnneeScolaireDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { LIBELLES_STATUT_ANNEE, StatutAnnee } from '../commun/etiquettes';
import { ServiceAnnees } from '../services/service-annees';
import { SectionCalendrier } from './section-calendrier';
import { SectionPeriodes } from './section-periodes';

type Action = 'ouvrir' | 'cloturer' | 'supprimer';

/** Confirmation de chaque action : titre, conséquence écrite, bouton nommé par l'action. */
const CONFIRMATIONS: Record<
  Action,
  { titre: string; consequence: string; action: string }
> = {
  ouvrir: {
    titre: "Ouvrir l'année",
    consequence:
      'Les paramètres de notation des cycles et des niveaux sont copiés pour cette année et ne changeront plus. Une seule année peut être en cours.',
    action: "Ouvrir l'année",
  },
  cloturer: {
    titre: "Clôturer l'année",
    consequence:
      "Définitif : l'année et tout ce qui lui est rattaché (inscriptions, périodes, calendrier) passent en lecture seule.",
    action: "Clôturer l'année",
  },
  supprimer: {
    titre: "Supprimer l'année",
    consequence:
      "L'année en préparation et ses périodes sont supprimées. Rien n'y est encore rattaché.",
    action: "Supprimer l'année",
  },
};

/** Années scolaires : création, ouverture, clôture, puis périodes et calendrier de l'année choisie. */
@Component({
  selector: 'ecole-page-annees',
  providers: [CHAMPS_FORMULAIRE],
  imports: [
    EnTetePage,
    DatePipe,
    RouterLink,
    MatButtonModule,
    SectionPeriodes,
    SectionCalendrier,
    Pastille,
    Confirmation,
  ],
  template: `
    <ecole-en-tete-page
      titre="Années scolaires et périodes"
      sousTitre="Années, périodes et calendrier"
    >
      @if (droits.peutEcrire()) {
        <a
          actions
          matButton="filled"
          routerLink="/etablissement/annees/preparation"
          data-testid="preparer-annee"
          >Préparer une année</a
        >
      }
    </ecole-en-tete-page>
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
              @if (droits.peutEcrire() && a.statut === 'PREPARATION') {
                <a
                  matButton
                  [routerLink]="['/etablissement/annees/preparation', a.id]"
                  [attr.data-testid]="'preparer-' + a.libelle"
                >
                  Continuer la préparation<span class="sr-only">
                    : {{ a.libelle }}</span
                  >
                </a>
              }
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

    @if (enAttente()) {
      <ecole-confirmation
        [titre]="texte().titre"
        [consequence]="texte().consequence"
        [action]="texte().action"
        testId="confirmer"
        [ouverte]="true"
        [occupe]="occupe()"
        [erreur]="erreurConfirmation()"
        (confirmee)="confirmer()"
        (annulee)="enAttente.set(null)"
      />
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
  styleUrls: ['../../../../ui/src/lib/styles/ecran.scss', './page-annees.scss'],
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
  protected readonly erreurConfirmation = signal<string | null>(null);
  /** Textes de la confirmation, avec le libellé de l'année dans le titre. */
  protected readonly texte = computed(() => {
    const attente = this.enAttente();
    if (!attente) {
      return { titre: '', consequence: '', action: '' };
    }
    const t = CONFIRMATIONS[attente.action];
    return { ...t, titre: `${t.titre} ${attente.annee.libelle ?? ''}`.trim() };
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
    this.erreurConfirmation.set(null);
    this.enAttente.set({ action, annee });
  }

  protected async confirmer(): Promise<void> {
    const attente = this.enAttente();
    if (!attente) {
      return;
    }
    // Erreur : la confirmation reste ouverte et l'affiche.
    this.erreurConfirmation.set(null);
    this.occupe.set(true);
    try {
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
      this.enAttente.set(null);
      await this.charger(action === 'supprimer' ? undefined : annee.id);
    } catch (e) {
      this.erreurConfirmation.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
