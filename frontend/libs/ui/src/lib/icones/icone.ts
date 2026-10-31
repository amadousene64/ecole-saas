import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';

/**
 * Icônes en SVG intégré (tracés de 24 × 24, style « Material Symbols Outlined ») : aucune police
 * d'icônes à télécharger, quelques centaines d'octets chacune. Décoratives : le texte voisin porte
 * le sens (aria-hidden).
 */
export const ICONES = {
  accueil: 'M6 19h3v-6h6v6h3v-9l-6-4.5L6 10v9Zm-2 2V9l8-6 8 6v12h-7v-6h-2v6H4Z',
  notes:
    'M9 17h6v-2H9v2Zm0-4h6v-2H9v2Zm-3 9q-.83 0-1.41-.59T4 20V4q0-.83.59-1.41T6 2h8l6 6v12q0 .83-.59 1.41T18 22H6Zm7-13V4H6v16h12V9h-5Z',
  paiements:
    'M13 13q-1.25 0-2.12-.88T10 10q0-1.25.88-2.12T13 7q1.25 0 2.13.88T16 10q0 1.25-.87 2.13T13 13Zm-7 4q-.83 0-1.41-.59T4 15V5q0-.83.59-1.41T6 3h14q.83 0 1.41.59T22 5v10q0 .83-.59 1.41T20 17H6Zm2-2h10q0-.83.59-1.41T20 13V7q-.83 0-1.41-.59T18 5H8q0 .83-.59 1.41T6 7v6q.83 0 1.41.59T8 15Zm11 6H2V8h2v11h15v2Z',
  messages:
    'M6 14h8v-2H6v2Zm0-3h12V9H6v2Zm0-3h12V6H6v2ZM2 22V4q0-.83.59-1.41T4 2h16q.83 0 1.41.59T22 4v12q0 .83-.59 1.41T20 18H6l-4 4Zm3.15-6H20V4H4v13.13L5.15 16Z',
  absence:
    'M12 22q-2.08 0-3.9-.79t-3.18-2.14q-1.35-1.35-2.14-3.17T2 12q0-2.08.79-3.9t2.14-3.18Q6.27 3.57 8.1 2.79 9.92 2 12 2t3.9.79q1.82.79 3.17 2.14t2.14 3.18Q22 9.92 22 12t-.79 3.9q-.79 1.82-2.14 3.17-1.35 1.35-3.17 2.14T12 22Zm0-2q1.35 0 2.6-.44t2.3-1.26L5.7 7.1q-.82 1.05-1.26 2.3T4 12q0 3.35 2.33 5.68T12 20Zm6.3-3.1q.82-1.05 1.26-2.3T20 12q0-3.35-2.33-5.68T12 4q-1.35 0-2.6.44T7.1 5.7l11.2 11.2Z',
  retard:
    'm15.3 16.7 1.4-1.4-3.7-3.7V7h-2v5.4l4.3 4.3ZM12 22q-2.08 0-3.9-.79t-3.18-2.14q-1.35-1.35-2.14-3.17T2 12q0-2.08.79-3.9t2.14-3.18Q6.27 3.57 8.1 2.79 9.92 2 12 2t3.9.79q1.82.79 3.17 2.14t2.14 3.18Q22 9.92 22 12t-.79 3.9q-.79 1.82-2.14 3.17-1.35 1.35-3.17 2.14T12 22Zm0-2q3.33 0 5.67-2.33T20 12q0-3.33-2.33-5.67T12 4Q8.67 4 6.33 6.33T4 12q0 3.33 2.33 5.67T12 20Z',
  billet:
    'M12 17q.42 0 .71-.29T13 16q0-.42-.29-.71T12 15q-.42 0-.71.29T11 16q0 .42.29.71T12 17Zm0-4q.42 0 .71-.29T13 12q0-.42-.29-.71T12 11q-.42 0-.71.29T11 12q0 .42.29.71T12 13Zm0-4q.42 0 .71-.29T13 8q0-.42-.29-.71T12 7q-.42 0-.71.29T11 8q0 .42.29.71T12 9Zm8 11H4q-.83 0-1.41-.59T2 18v-4q.83 0 1.41-.59T4 12q0-.83-.59-1.41T2 10V6q0-.83.59-1.41T4 4h16q.83 0 1.41.59T22 6v4q-.83 0-1.41.59T20 12q0 .83.59 1.41T22 14v4q0 .83-.59 1.41T20 20Zm0-2v-2.55q-.92-.55-1.46-1.46T18 12q0-1.08.54-1.99T20 8.55V6H4v2.55q.92.55 1.46 1.46T6 12q0 1.08-.54 1.99T4 15.45V18h16Z',
  info: 'M11 17h2v-6h-2v6Zm1-8q.42 0 .71-.29T13 8q0-.42-.29-.71T12 7q-.42 0-.71.29T11 8q0 .42.29.71T12 9Zm0 13q-2.08 0-3.9-.79t-3.18-2.14q-1.35-1.35-2.14-3.17T2 12q0-2.08.79-3.9t2.14-3.18Q6.27 3.57 8.1 2.79 9.92 2 12 2t3.9.79q1.82.79 3.17 2.14t2.14 3.18Q22 9.92 22 12t-.79 3.9q-.79 1.82-2.14 3.17-1.35 1.35-3.17 2.14T12 22Zm0-2q3.35 0 5.68-2.33T20 12q0-3.35-2.33-5.68T12 4Q8.65 4 6.33 6.33T4 12q0 3.35 2.33 5.68T12 20Z',
} as const;

export type NomIcone = keyof typeof ICONES;

@Component({
  selector: 'ecole-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'ecole-icone' },
  template: `
    <svg
      viewBox="0 0 24 24"
      [attr.width]="taille()"
      [attr.height]="taille()"
      focusable="false"
    >
      <path [attr.d]="trace()" fill="currentColor" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      line-height: 0;
    }
  `,
})
export class Icone {
  readonly nom = input.required<NomIcone>();
  readonly taille = input(24);
  protected readonly trace = computed(() => ICONES[this.nom()]);
}
