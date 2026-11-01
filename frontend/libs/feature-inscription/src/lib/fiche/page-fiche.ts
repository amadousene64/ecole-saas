import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EleveDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceEleves } from '../services/service-eleves';
import { ServiceResponsables } from '../services/service-responsables';
import { DialogueCode } from './dialogue-code';
import { OngletIdentite } from './onglet-identite';
import { OngletPieces } from './onglet-pieces';
import { OngletResponsables } from './onglet-responsables';
import { OngletScolarite } from './onglet-scolarite';

/**
 * Fiche d'un élève : onglets Identité, Responsables, Scolarité et Pièces. Un onglet n'apparaît que
 * si le rôle peut au moins lire son contenu (confort d'interface : le serveur reste l'autorité).
 */
@Component({
  selector: 'ecole-page-fiche',
  imports: [
    MatButtonModule,
    MatTabsModule,
    RouterLink,
    OngletIdentite,
    OngletResponsables,
    OngletScolarite,
    OngletPieces,
  ],
  template: `
    <p><a routerLink="/eleves">← Retour à la liste</a></p>
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-fiche">
        {{ message }}
      </p>
    }
    @if (eleve(); as e) {
      <h1 data-testid="titre-fiche">{{ e.prenoms }} {{ e.nom }}</h1>
      <p class="aide">
        Matricule <span class="mono">{{ e.matricule }}</span>
        @if (e.compteActive) {
          · compte élève activé
        }
      </p>
      @if (droits.peutEcrire() && !e.compteActive) {
        <div class="actions">
          <button
            matButton
            type="button"
            (click)="codeEleve(e)"
            data-testid="code-eleve"
          >
            Code d'activation de l'élève
          </button>
        </div>
      }
      <mat-tab-group animationDuration="0ms" data-testid="onglets-fiche">
        <mat-tab label="Identité">
          <ng-template matTabContent
            ><ecole-onglet-identite [eleve]="e"
          /></ng-template>
        </mat-tab>
        @if (droits.voitResponsables()) {
          <mat-tab label="Responsables">
            <ng-template matTabContent
              ><ecole-onglet-responsables [eleveId]="id"
            /></ng-template>
          </mat-tab>
        }
        @if (droits.voitScolarite()) {
          <mat-tab label="Scolarité">
            <ng-template matTabContent
              ><ecole-onglet-scolarite [eleveId]="id"
            /></ng-template>
          </mat-tab>
        }
        @if (droits.voitPieces()) {
          <mat-tab label="Pièces">
            <ng-template matTabContent
              ><ecole-onglet-pieces [eleveId]="id"
            /></ng-template>
          </mat-tab>
        }
      </mat-tab-group>
    }
  `,
  styles: [STYLES_COMMUNS],
})
export class PageFiche {
  private readonly service = inject(ServiceEleves);
  private readonly responsables = inject(ServiceResponsables);
  private readonly dialogue = inject(MatDialog);
  protected readonly droits = inject(Droits);

  protected readonly id =
    inject(ActivatedRoute).snapshot.paramMap.get('eleveId') ?? '';
  protected readonly eleve = signal<EleveDto | null>(null);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      this.eleve.set(await this.service.lire(this.id));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected async codeEleve(eleve: EleveDto): Promise<void> {
    this.erreur.set(null);
    try {
      const reponse = await this.responsables.codeEleve(this.id);
      this.dialogue.open(DialogueCode, {
        data: {
          titulaire: `${eleve.prenoms} ${eleve.nom}`,
          code: reponse.code ?? '',
          expireLe: reponse.expireLe,
        },
        disableClose: true,
        autoFocus: 'first-tabbable',
      });
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }
}
