import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { STYLES_COMMUNS } from '../commun/styles';
import { SectionCycles } from './section-cycles';
import { SectionNiveaux } from './section-niveaux';
import { SectionSeries } from './section-series';

/** Structure pédagogique : cycles, niveaux et séries de l'école. */
@Component({
  selector: 'ecole-page-structure',
  imports: [MatTabsModule, SectionCycles, SectionNiveaux, SectionSeries],
  template: `
    <h1>Structure pédagogique</h1>
    <mat-tab-group animationDuration="0ms">
      <mat-tab label="Cycles">
        <ng-template matTabContent><ecole-section-cycles /></ng-template>
      </mat-tab>
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
export class PageStructure {}
