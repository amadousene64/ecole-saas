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
  styles: `
    .centre {
      display: flex;
      justify-content: center;
      padding: 48px 16px;
    }
    .visuellement-cache {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }
  `,
})
export class Chargement {
  readonly message = input<string>(TEXTES.chargement);
}
