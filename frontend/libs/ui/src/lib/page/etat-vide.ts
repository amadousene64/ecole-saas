import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** État vide d'une liste : ce qui manque, pourquoi, et l'action pour y remédier (projetée). */
@Component({
  selector: 'ecole-etat-vide',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="vide carte" [attr.data-testid]="'etat-vide'">
      <h2>{{ titre() }}</h2>
      @if (detail(); as texte) {
        <p class="texte-secondaire">{{ texte }}</p>
      }
      <div class="action"><ng-content /></div>
    </section>
  `,
  styles: `
    .vide {
      text-align: center;
      padding: 32px 16px;
    }
    h2 {
      margin: 0 0 8px;
    }
    .action {
      margin-top: 16px;
      display: flex;
      justify-content: center;
      gap: 8px;
    }
  `,
})
export class EtatVide {
  readonly titre = input.required<string>();
  readonly detail = input<string | null>(null);
}
