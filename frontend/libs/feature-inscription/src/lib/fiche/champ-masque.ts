import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/**
 * Donnée sensible : masquée tant qu'on n'a pas demandé à la voir. La valeur n'entre dans le DOM
 * qu'après « Afficher » (et en sort au remasquage) ; la mention rappelle que la consultation est
 * journalisée côté serveur.
 */
@Component({
  selector: 'ecole-champ-masque',
  imports: [MatButtonModule],
  template: `
    <div class="champ" [attr.data-testid]="'sensible-' + cle()">
      <span class="libelle" [id]="'lib-' + cle()">{{ libelle() }}</span>
      @if (valeur() === null) {
        <span class="masque" aria-hidden="true">••••••••</span>
        <span class="sr-only">Valeur masquée</span>
      } @else {
        <span class="valeur" [attr.aria-labelledby]="'lib-' + cle()">{{
          valeur() || 'Non renseigné'
        }}</span>
      }
    </div>
    @if (peutAfficher() && valeur() === null) {
      <button
        matButton
        type="button"
        (click)="afficher.emit()"
        [attr.data-testid]="'afficher-' + cle()"
      >
        Afficher<span class="sr-only"> : {{ libelle() }}</span>
      </button>
    }
  `,
  styles: `
    .champ {
      display: flex;
      flex-direction: column;
      margin-bottom: 4px;
    }
    .valeur {
      white-space: pre-wrap;
    }
  `,
})
export class ChampMasque {
  readonly cle = input.required<string>();
  readonly libelle = input.required<string>();
  /** null : masqué ; chaîne (éventuellement vide) : affiché. */
  readonly valeur = input<string | null>(null);
  readonly peutAfficher = input(false);
  readonly afficher = output<void>();
}
