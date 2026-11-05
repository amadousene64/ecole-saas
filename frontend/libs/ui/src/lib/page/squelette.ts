import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';

/**
 * Squelette de chargement (à la place d'un spinner) : des barres grisées de la forme du contenu à
 * venir. Annoncé comme « chargement » aux lecteurs d'écran ; sans animation si l'utilisateur le
 * demande.
 */
@Component({
  selector: 'ecole-squelette',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="squelette"
      role="status"
      aria-busy="true"
      data-testid="squelette"
    >
      <span class="sr-only">Chargement en cours…</span>
      @for (ligne of lignesListe(); track ligne) {
        <span
          class="barre"
          [class.carte]="variante() === 'cartes'"
          aria-hidden="true"
        ></span>
      }
    </div>
  `,
  styleUrl: './squelette.scss',
})
export class Squelette {
  readonly lignes = input(5);
  readonly variante = input<'lignes' | 'cartes'>('lignes');
  protected readonly lignesListe = computed(() =>
    Array.from({ length: this.lignes() }, (_, i) => i),
  );
}
