import { Component } from '@angular/core';
import { CoquilleApplicationLegere } from '@ecole/auth';

/** Barre du bas générée par la table des droits ; masquée tant qu'il y a moins de deux entrées. */
@Component({
  imports: [CoquilleApplicationLegere],
  selector: 'ecole-root',
  template: '<ecole-coquille-application-legere titre="Espace parent" />',
})
export class App {}
