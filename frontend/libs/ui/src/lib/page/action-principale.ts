import { Component, ViewEncapsulation } from '@angular/core';

/**
 * Action principale d'une page. Au-dessus de 600 px, c'est un bouton ordinaire dans l'en-tête ; en
 * dessous, il devient un bouton flottant en bas à droite (au-dessus de la barre du bas s'il y en a
 * une). À placer dans l'emplacement `actions` de {@link EnTetePage}, avec un seul bouton ou lien.
 */
@Component({
  selector: 'ecole-action-principale',
  encapsulation: ViewEncapsulation.None,
  template: `<ng-content />`,
  host: { class: 'action-principale' },
  styleUrl: './action-principale.scss',
})
export class ActionPrincipale {}
