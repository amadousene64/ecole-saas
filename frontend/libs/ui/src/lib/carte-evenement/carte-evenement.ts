import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icone, NomIcone } from '../icones/icone';
import { TonStatut } from '../pastille/pastille';

/**
 * Événement de l'espace parent (absence, retard, billet, note, paiement…) : icône à gauche, titre,
 * détail, moment, et UNE action principale (lien interne ou bouton). Sans Material.
 */
@Component({
  selector: 'ecole-carte-evenement',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone, RouterLink],
  template: `
    <article class="carte evenement">
      <span class="pictogramme" [class]="ton()">
        <ecole-icone [nom]="icone()" />
      </span>
      <div class="contenu">
        <h3 class="titre">{{ titre() }}</h3>
        @if (detail(); as texte) {
          <p class="detail">{{ texte }}</p>
        }
        @if (moment(); as quand) {
          <p class="moment libelle texte-secondaire">{{ quand }}</p>
        }
      </div>
      @if (action(); as libelle) {
        @if (lien(); as chemin) {
          <a class="bouton secondaire" [routerLink]="chemin">{{ libelle }}</a>
        } @else {
          <button
            class="bouton secondaire"
            type="button"
            (click)="actionDemandee.emit()"
          >
            {{ libelle }}
          </button>
        }
      }
    </article>
  `,
  styles: `
    .evenement {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 4px 14px;
      align-items: start;
    }
    .pictogramme {
      grid-row: span 2;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: var(--ecole-rayon-controle);
      background: var(--ecole-fond);
      color: var(--ecole-texte-secondaire);
    }
    .pictogramme.primaire {
      background: var(--ecole-primaire-doux);
      color: var(--ecole-primaire);
    }
    .pictogramme.safran {
      background: var(--ecole-safran-fond);
      color: var(--ecole-safran);
    }
    .pictogramme.bissap {
      background: var(--ecole-bissap-fond);
      color: var(--ecole-bissap);
    }
    .pictogramme.fleuve {
      background: var(--ecole-fleuve-fond);
      color: var(--ecole-fleuve);
    }
    .titre {
      margin: 0;
      font-size: inherit;
      font-weight: 600;
    }
    .detail,
    .moment {
      margin: 2px 0 0;
    }
    .bouton {
      grid-column: 2;
      justify-self: start;
      margin-top: 8px;
    }
  `,
})
export class CarteEvenement {
  readonly icone = input.required<NomIcone>();
  readonly ton = input<TonStatut>('neutre');
  readonly titre = input.required<string>();
  readonly detail = input<string | null>(null);
  /** Moment lisible, déjà formaté (« Aujourd'hui, 8 h 05 »). */
  readonly moment = input<string | null>(null);
  /** Libellé de l'unique action ; sans action, la carte est purement informative. */
  readonly action = input<string | null>(null);
  /** Lien interne de l'action ; sans lien, l'action émet {@link actionDemandee}. */
  readonly lien = input<string | null>(null);
  readonly actionDemandee = output<void>();
}
