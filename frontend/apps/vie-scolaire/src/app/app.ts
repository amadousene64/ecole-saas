import { Component } from '@angular/core';
import { CoquilleApplication } from '@ecole/auth';
import { LienNavigation } from '@ecole/ui';

@Component({
  imports: [CoquilleApplication],
  selector: 'ecole-root',
  template:
    '<ecole-coquille-application titre="Vie scolaire" [liens]="liens" />',
})
export class App {
  protected readonly liens: LienNavigation[] = [
    { chemin: '/', libelle: 'Accueil' },
  ];
}
