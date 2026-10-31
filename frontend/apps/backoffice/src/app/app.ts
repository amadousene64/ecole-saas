import { Component } from '@angular/core';
import { CoquilleApplication } from '@ecole/auth';
import { LIENS_ETABLISSEMENT } from '@ecole/feature-etablissement';
import { LIENS_INSCRIPTION } from '@ecole/feature-inscription';
import { LienNavigation } from '@ecole/ui';

@Component({
  imports: [CoquilleApplication],
  selector: 'ecole-root',
  template:
    '<ecole-coquille-application titre="Back-office" [liens]="liens" />',
})
export class App {
  protected readonly liens: LienNavigation[] = [
    { chemin: '/', libelle: 'Accueil' },
    ...LIENS_INSCRIPTION,
    ...LIENS_ETABLISSEMENT,
  ];
}
