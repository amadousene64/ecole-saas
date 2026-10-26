import { Component } from '@angular/core';
import { CoquilleApplicationLegere } from '@ecole/auth';
import { LienNavigation } from '@ecole/ui';

@Component({
  imports: [CoquilleApplicationLegere],
  selector: 'ecole-root',
  template:
    '<ecole-coquille-application-legere titre="Espace parent" [liens]="liens" />',
})
export class App {
  protected readonly liens: LienNavigation[] = [
    { chemin: '/', libelle: 'Accueil' },
  ];
}
