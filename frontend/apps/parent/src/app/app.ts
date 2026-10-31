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
  /** Barre de navigation basse : quatre entrées. */
  protected readonly liens: LienNavigation[] = [
    { chemin: '/', libelle: 'Accueil', icone: 'accueil' },
    { chemin: '/notes', libelle: 'Notes', icone: 'notes' },
    { chemin: '/paiements', libelle: 'Paiements', icone: 'paiements' },
    { chemin: '/messages', libelle: 'Messages', icone: 'messages' },
  ];
}
