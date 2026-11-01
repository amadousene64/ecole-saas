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
  styles: `
    @media (max-width: 599.98px) {
      ecole-action-principale.action-principale {
        position: fixed;
        right: 16px;
        bottom: calc(
          16px + var(--ecole-hauteur-barre-basse, 0px) +
            env(safe-area-inset-bottom, 0px)
        );
        z-index: 900;
      }
      ecole-action-principale.action-principale > * {
        min-height: 56px;
        border-radius: 28px !important;
        padding-inline: 20px !important;
        box-shadow: 0 4px 12px rgb(0 0 0 / 0.3);
      }
    }
  `,
})
export class ActionPrincipale {}
