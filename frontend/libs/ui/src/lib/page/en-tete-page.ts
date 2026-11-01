import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * En-tête de page homogène : titre (h1), sous-titre facultatif, et à droite les actions
 * (projetées avec l'attribut `actions`). Sans Material : utilisable aussi par l'app parent.
 */
@Component({
  selector: 'ecole-en-tete-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="en-tete">
      <div class="textes">
        <h1>{{ titre() }}</h1>
        @if (sousTitre(); as texte) {
          <p class="sous-titre texte-secondaire">{{ texte }}</p>
        }
      </div>
      <div class="actions-page">
        <ng-content select="[actions]" />
      </div>
    </header>
  `,
  styles: `
    .en-tete {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 8px 16px;
      margin-bottom: 16px;
    }
    h1 {
      margin: 0;
    }
    .sous-titre {
      margin: 4px 0 0;
    }
    .actions-page {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
  `,
})
export class EnTetePage {
  readonly titre = input.required<string>();
  readonly sousTitre = input<string | null>(null);
}
