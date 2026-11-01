import { Component, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { EnTetePage } from '@ecole/ui';
import { Droits } from '../commun/droits';
import { STYLES_COMMUNS } from '../commun/styles';
import { SectionCycles } from './section-cycles';
import { SectionNiveaux } from './section-niveaux';
import { SectionSeries } from './section-series';

/**
 * Structure pédagogique : cycles, niveaux et séries de l'école. Les cycles et leurs paramètres de
 * notation sont au directeur seul (le serveur refuse tout autre rôle) : le secrétariat ne voit que
 * les niveaux et les séries.
 */
@Component({
  selector: 'ecole-page-structure',
  imports: [
    EnTetePage,
    MatTabsModule,
    SectionCycles,
    SectionNiveaux,
    SectionSeries,
  ],
  template: `
    <ecole-en-tete-page
      titre="Structure pédagogique"
      [sousTitre]="
        droits.estDirecteur() ? 'Cycles, niveaux et séries' : 'Niveaux et séries'
      "
    />
    <mat-tab-group animationDuration="0ms">
      @if (droits.estDirecteur()) {
        <mat-tab label="Cycles">
          <ng-template matTabContent><ecole-section-cycles /></ng-template>
        </mat-tab>
      }
      <mat-tab label="Niveaux">
        <ng-template matTabContent><ecole-section-niveaux /></ng-template>
      </mat-tab>
      <mat-tab label="Séries">
        <ng-template matTabContent><ecole-section-series /></ng-template>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: [STYLES_COMMUNS],
})
export class PageStructure {
  protected readonly droits = inject(Droits);
}
