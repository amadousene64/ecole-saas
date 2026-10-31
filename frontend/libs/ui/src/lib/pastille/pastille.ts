import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Ton d'une pastille :
 * - primaire : état normal, en cours, réglé ;
 * - safran : retard, avertissement ;
 * - bissap : absence, erreur, blocage ;
 * - fleuve : information, billet ;
 * - neutre : sans signal (en préparation, clôturé…).
 */
export type TonStatut = 'primaire' | 'safran' | 'bissap' | 'fleuve' | 'neutre';

/**
 * Badge de statut en pastille : un point ET un texte, jamais la couleur seule. Styles globaux
 * (.pastille, voir theme/_base.scss) : utilisable aussi dans l'app parent, sans Material.
 */
@Component({
  selector: 'ecole-pastille',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="pastille" [class]="ton()"><ng-content /></span>`,
})
export class Pastille {
  readonly ton = input<TonStatut>('neutre');
}
