import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { SectionClasses } from './section-classes';
import { SectionSalles } from './section-salles';
import { EnTetePage, CHAMPS_FORMULAIRE } from '@ecole/ui';

/** Classes d'une année et salles de l'école. */
@Component({
  selector: 'ecole-page-classes',
  providers: [CHAMPS_FORMULAIRE],
  imports: [EnTetePage, MatTabsModule, SectionClasses, SectionSalles],
  template: `
    <ecole-en-tete-page
      titre="Classes et salles"
      sousTitre="Classes d'une année et salles de l'école"
    />
    <mat-tab-group animationDuration="0ms">
      <mat-tab label="Classes">
        <ng-template matTabContent><ecole-section-classes /></ng-template>
      </mat-tab>
      <mat-tab label="Salles">
        <ng-template matTabContent><ecole-section-salles /></ng-template>
      </mat-tab>
    </mat-tab-group>
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class PageClasses {}
