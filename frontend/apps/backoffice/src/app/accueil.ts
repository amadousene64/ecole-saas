import { NgComponentOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  signal,
  Type,
} from '@angular/core';
import { Acces, PageAccueil, PageMoi } from '@ecole/auth';

/**
 * Accueil du back-office. La direction y trouve le tableau de bord ; les autres rôles gardent
 * l'accueil simple avec leurs raccourcis. Le serveur refuse les statistiques à tout autre rôle :
 * ce choix n'est qu'un confort d'affichage.
 *
 * Le tableau de bord (et ECharts avec lui) est chargé par un `import()` dynamique, seulement pour
 * la direction : il reste hors du lot initial (budget de taille de l'application). Un `@defer`
 * ferait entrer son propre moteur dans ce lot, d'où le chargement explicite.
 */
@Component({
  selector: 'ecole-accueil-backoffice',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgComponentOutlet, PageAccueil, PageMoi],
  template: `
    @if (acces.peut('statistiques.voir')) {
      @if (tableau(); as page) {
        <ng-container [ngComponentOutlet]="page" />
      } @else if (echec()) {
        <p role="alert" data-testid="tableau-indisponible">
          Le tableau de bord n'a pas pu être chargé. Rechargez la page.
        </p>
      } @else {
        <p class="sr-only" role="status">Chargement du tableau de bord…</p>
      }
      <ecole-page-moi [integre]="true" />
    } @else {
      <ecole-page-accueil />
    }
  `,
})
export class AccueilBackoffice {
  protected readonly acces = inject(Acces);
  protected readonly tableau = signal<Type<unknown> | null>(null);
  /** Le lot du tableau de bord n'a pas pu être téléchargé (réseau coupé, nouvelle version). */
  protected readonly echec = signal(false);

  constructor() {
    effect(() => {
      if (this.acces.peut('statistiques.voir') && this.tableau() === null) {
        import('@ecole/feature-statistique')
          .then((m) => this.tableau.set(m.PageTableauDeBord))
          .catch(() => this.echec.set(true));
      }
    });
  }
}
