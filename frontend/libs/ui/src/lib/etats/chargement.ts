import { Component, input } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TEXTES } from '../textes';

@Component({
  selector: 'ecole-chargement',
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="centre" role="status">
      <mat-progress-spinner
        mode="indeterminate"
        diameter="40"
        [attr.aria-label]="message()"
      />
      <span class="visuellement-cache">{{ message() }}</span>
    </div>
  `,
  styleUrl: './chargement.scss',
})
export class Chargement {
  readonly message = input<string>(TEXTES.chargement);
}
