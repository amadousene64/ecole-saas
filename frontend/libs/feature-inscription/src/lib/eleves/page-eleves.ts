import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { ClasseDto, EleveListeDto } from '@ecole/api-client';
import {
  ActionPrincipale,
  EnTetePage,
  EtatVide,
  Pastille,
  Squelette,
} from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import {
  STATUTS_INSCRIPTION,
  libelleStatut,
  tonStatut,
} from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceEleves, StatutInscription } from '../services/service-eleves';

const TAILLE_PAGE = 25;
const DELAI_RECHERCHE_MS = 300;

/**
 * Liste des élèves : recherche (nom, matricule), filtres classe et statut, pagination par curseur.
 * Chaque ligne montre le statut de la dernière inscription et l'état du dossier (pièces
 * manquantes, en analyse, refusées) ; ce dernier n'est fourni qu'aux rôles qui lisent les pièces.
 */
@Component({
  selector: 'ecole-page-eleves',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    Pastille,
    EnTetePage,
    ActionPrincipale,
    EtatVide,
    Squelette,
  ],
  template: `
    <ecole-en-tete-page
      titre="Élèves"
      sousTitre="Recherche, inscriptions et dossiers"
    >
      @if (droits.peutImporter()) {
        <a actions matButton routerLink="import" data-testid="lien-import"
          >Importer un fichier</a
        >
      }
      @if (droits.peutCreer()) {
        <ecole-action-principale actions>
          <a matButton="filled" routerLink="nouveau" data-testid="nouvel-eleve"
            >Nouvel élève</a
          >
        </ecole-action-principale>
      }
    </ecole-en-tete-page>

    <form class="grille" role="search" (submit)="$event.preventDefault()">
      <mat-form-field appearance="outline">
        <mat-label>Nom ou matricule</mat-label>
        <input
          matInput
          type="search"
          [ngModel]="recherche()"
          (ngModelChange)="rechercher($event)"
          name="recherche"
          autocomplete="off"
          data-testid="filtre-recherche"
        />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Classe</mat-label>
        <mat-select
          [value]="classe()"
          (selectionChange)="filtrerClasse($event.value)"
          data-testid="filtre-classe"
        >
          <mat-option value="">Toutes les classes</mat-option>
          @for (c of classes(); track c.id) {
            <mat-option [value]="c.id">{{ c.nom }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Statut</mat-label>
        <mat-select
          [value]="statut()"
          (selectionChange)="filtrerStatut($event.value)"
          data-testid="filtre-statut"
        >
          <mat-option value="">Tous les statuts</mat-option>
          @for (s of statuts; track s.valeur) {
            <mat-option [value]="s.valeur">{{ s.libelle }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </form>

    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-liste">
        {{ message }}
      </p>
    }

    <table class="cartes" data-testid="table-eleves">
      <caption class="sr-only">
        Liste des élèves
      </caption>
      <thead>
        <tr>
          <th scope="col">Matricule</th>
          <th scope="col">Nom</th>
          <th scope="col">Classe</th>
          <th scope="col">Statut</th>
          <th scope="col">Dossier</th>
        </tr>
      </thead>
      <tbody>
        @for (e of lignes(); track e.id) {
          <tr [attr.data-testid]="'eleve-' + e.matricule">
            <td class="mono" data-label="Matricule">{{ e.matricule }}</td>
            <td data-label="Nom">
              <a [routerLink]="['/eleves', e.id]"
                >{{ e.prenoms }} {{ e.nom }}</a
              >
            </td>
            <td data-label="Classe">{{ e.classe || '—' }}</td>
            <td data-label="Statut">
              @if (e.statut) {
                <ecole-pastille [ton]="ton(e.statut)">{{
                  libelle(e.statut)
                }}</ecole-pastille>
              } @else {
                <span class="aide">Jamais inscrit</span>
              }
            </td>
            <td data-label="Dossier">
              @if (e.dossier; as d) {
                @if (!d.manquantes?.length && !d.enAnalyse && !d.refusees) {
                  <ecole-pastille ton="primaire">Complet</ecole-pastille>
                }
                @if (d.manquantes?.length; as n) {
                  <ecole-pastille ton="safran"
                    >{{ n }} pièce{{ n > 1 ? 's' : '' }} manquante{{
                      n > 1 ? 's' : ''
                    }}</ecole-pastille
                  >
                }
                @if (d.enAnalyse) {
                  <ecole-pastille ton="fleuve"
                    >{{ d.enAnalyse }} en analyse</ecole-pastille
                  >
                }
                @if (d.refusees) {
                  <ecole-pastille ton="bissap"
                    >{{ d.refusees }} refusée{{
                      d.refusees > 1 ? 's' : ''
                    }}</ecole-pastille
                  >
                }
              } @else {
                <span class="aide">—</span>
              }
            </td>
          </tr>
        }
      </tbody>
    </table>
    @if (chargement() && lignes().length === 0) {
      <ecole-squelette [lignes]="6" variante="cartes" />
    } @else if (!chargement() && lignes().length === 0 && !erreur()) {
      <ecole-etat-vide
        titre="Aucun élève ne correspond"
        detail="Modifiez la recherche ou les filtres, ou ajoutez un élève."
      >
        @if (droits.peutCreer()) {
          <a matButton="filled" routerLink="nouveau">Nouvel élève</a>
        }
      </ecole-etat-vide>
    }

    <div aria-live="polite" class="aide" data-testid="etat-liste">
      @if (chargement()) {
        Chargement en cours…
      } @else {
        {{ lignes().length }} élève{{
          lignes().length > 1 ? 's' : ''
        }}
        affiché{{ lignes().length > 1 ? 's' : '' }}
      }
    </div>
    @if (suivant()) {
      <div class="actions">
        <button
          matButton
          type="button"
          [disabled]="chargement()"
          (click)="chargerSuite()"
          data-testid="charger-suite"
        >
          Charger la suite
        </button>
      </div>
    }
  `,
  styles: [STYLES_COMMUNS],
})
export class PageEleves {
  private readonly service = inject(ServiceEleves);
  protected readonly droits = inject(Droits);

  protected readonly statuts = STATUTS_INSCRIPTION;
  protected readonly lignes = signal<EleveListeDto[]>([]);
  protected readonly suivant = signal<string | null>(null);
  protected readonly classes = signal<ClasseDto[]>([]);
  protected readonly recherche = signal('');
  protected readonly classe = signal('');
  protected readonly statut = signal<StatutInscription | ''>('');
  protected readonly chargement = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly vide = computed(() => this.lignes().length === 0);

  /** Numéro de la dernière demande : une réponse périmée (frappe rapide) est ignorée. */
  private demande = 0;
  private minuterie: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    void this.chargerClasses();
    void this.charger(false);
  }

  protected libelle = libelleStatut;
  protected ton = tonStatut;

  protected rechercher(texte: string): void {
    this.recherche.set(texte);
    clearTimeout(this.minuterie);
    this.minuterie = setTimeout(
      () => void this.charger(false),
      DELAI_RECHERCHE_MS,
    );
  }

  protected filtrerClasse(id: string): void {
    this.classe.set(id);
    void this.charger(false);
  }

  protected filtrerStatut(valeur: StatutInscription | ''): void {
    this.statut.set(valeur);
    void this.charger(false);
  }

  protected chargerSuite(): Promise<void> {
    return this.charger(true);
  }

  private async chargerClasses(): Promise<void> {
    try {
      this.classes.set(await this.service.classes());
    } catch {
      // Le filtre par classe est un confort : la liste reste utilisable sans lui.
    }
  }

  private async charger(suite: boolean): Promise<void> {
    const numero = ++this.demande;
    this.chargement.set(true);
    this.erreur.set(null);
    try {
      const page = await this.service.rechercher({
        recherche: this.recherche().trim() || undefined,
        classe: this.classe() || undefined,
        statut: this.statut() || undefined,
        curseur: suite ? (this.suivant() ?? undefined) : undefined,
        taille: TAILLE_PAGE,
      });
      if (numero !== this.demande) {
        return;
      }
      const elements = page.elements ?? [];
      this.lignes.set(suite ? [...this.lignes(), ...elements] : elements);
      this.suivant.set(page.curseurSuivant ?? null);
    } catch (e) {
      if (numero === this.demande) {
        this.erreur.set(messageErreur(e));
      }
    } finally {
      if (numero === this.demande) {
        this.chargement.set(false);
      }
    }
  }
}
