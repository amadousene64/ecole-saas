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
  styles: `
    .squelette {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .barre {
      display: block;
      height: 20px;
      border-radius: 6px;
      background: linear-gradient(
        90deg,
        var(--ecole-bordure, #e2e6e3) 25%,
        var(--ecole-primaire-doux, #f0f4f1) 50%,
        var(--ecole-bordure, #e2e6e3) 75%
      );
      background-size: 200% 100%;
      animation: ecole-squelette 1.4s ease-in-out infinite;
    }
    .barre.carte {
      height: 72px;
      border-radius: var(--ecole-rayon-carte, 12px);
    }
    @keyframes ecole-squelette {
      from {
        background-position: 200% 0;
      }
      to {
        background-position: -200% 0;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .barre {
        animation: none;
      }
    }
  `,
})
export class Squelette {
  readonly lignes = input(5);
  readonly variante = input<'lignes' | 'cartes'>('lignes');
  protected readonly lignesListe = computed(() =>
    Array.from({ length: this.lignes() }, (_, i) => i),
  );
}
