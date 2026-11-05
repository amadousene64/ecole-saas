import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/** Page d'état (erreur, accès refusé, aucune école…) : titre, détail et une action facultative. */
@Component({
  selector: 'ecole-etat-page',
  imports: [MatButtonModule],
  template: `
    <section class="etat" [attr.role]="alerte() ? 'alert' : null">
      <h1>{{ titre() }}</h1>
      <p>{{ detail() }}</p>
      @if (action(); as libelle) {
        <button
          matButton="filled"
          type="button"
          (click)="actionDemandee.emit()"
        >
          {{ libelle }}
        </button>
      }
    </section>
  `,
  styleUrl: './etat-page.scss',
})
export class EtatPage {
  readonly titre = input.required<string>();
  readonly detail = input.required<string>();
  readonly action = input<string | null>(null);
  readonly alerte = input(false);
  readonly actionDemandee = output<void>();
}
