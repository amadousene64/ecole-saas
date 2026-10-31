import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

/** Rubrique pas encore ouverte (notes, paiements, messages) : page d'attente sans Material. */
@Component({
  selector: 'ecole-bientot',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ titre() }}</h1>
    <p class="carte texte-secondaire">
      Cette rubrique sera bientôt disponible.
    </p>
  `,
})
export class PageBientot {
  protected readonly titre = toSignal(
    inject(ActivatedRoute).data.pipe(map((d) => d['titre'] as string)),
    { initialValue: '' },
  );
}
