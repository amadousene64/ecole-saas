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
  styleUrl: './etat-vide.scss',
})
export class EtatVide {
  readonly titre = input.required<string>();
  readonly detail = input<string | null>(null);
}
