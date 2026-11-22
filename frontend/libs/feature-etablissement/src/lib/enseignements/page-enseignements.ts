import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { CHAMPS_FORMULAIRE, EnTetePage } from '@ecole/ui';
import { SectionGrille } from './section-grille';
import { SectionMatieres } from './section-matieres';

/**
 * Matières et horaires : matières de l'école et grille horaire de chaque cycle, socle de
 * l'emploi du temps et de l'appel. Direction, censeur et secrétariat les modifient ; le reste du
 * personnel les lit (le serveur contrôle chaque appel).
 */
@Component({
  selector: 'ecole-page-enseignements',
  providers: [CHAMPS_FORMULAIRE],
  imports: [EnTetePage, MatTabsModule, SectionGrille, SectionMatieres],
  template: `
    <ecole-en-tete-page
      titre="Matières et horaires"
      sousTitre="Matières enseignées et grille horaire de chaque cycle"
    />
    <mat-tab-group
      mat-stretch-tabs="false"
      mat-align-tabs="start"
      animationDuration="0ms"
    >
      <mat-tab label="Matières">
        <ng-template matTabContent><ecole-section-matieres /></ng-template>
      </mat-tab>
      <mat-tab label="Grille horaire">
        <ng-template matTabContent><ecole-section-grille /></ng-template>
      </mat-tab>
    </mat-tab-group>
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class PageEnseignements {}
