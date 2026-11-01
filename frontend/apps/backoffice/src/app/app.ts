import { Component } from '@angular/core';
import { CoquilleApplication } from '@ecole/auth';

/** Le menu vient de la table des droits (`@ecole/auth`) : plus aucune liste de liens ici. */
@Component({
  imports: [CoquilleApplication],
  selector: 'ecole-root',
  template: '<ecole-coquille-application titre="Back-office" />',
})
export class App {}
