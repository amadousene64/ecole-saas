import { Component } from '@angular/core';
import { CoquilleApplication } from '@ecole/auth';

@Component({
  imports: [CoquilleApplication],
  selector: 'ecole-root',
  template: '<ecole-coquille-application titre="Vie scolaire" />',
})
export class App {}
