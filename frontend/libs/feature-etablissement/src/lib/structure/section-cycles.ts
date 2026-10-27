import { Component, inject, signal } from '@angular/core';
import { CycleDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { ServiceStructure } from '../services/service-structure';
import { CarteCycle } from './carte-cycle';

/** Les trois cycles du système sénégalais et leurs paramètres de notation (modèle de l'école). */
@Component({
  selector: 'ecole-section-cycles',
  imports: [CarteCycle],
  template: `
    <p class="aide" data-testid="aide-cycles">
      Ces paramètres sont le modèle de l'école : une année scolaire en prend une
      copie à son ouverture et la conserve. Les modifier n'agit que sur les
      années à venir.
      @if (!droits.estDirecteur()) {
        Seul le directeur peut les modifier.
      }
    </p>
    @if (erreur(); as message) {
      <p class="erreur" role="alert">{{ message }}</p>
    }
    @for (cycle of cycles(); track cycle.id) {
      <ecole-carte-cycle
        [cycle]="cycle"
        [modifiable]="droits.estDirecteur()"
        (enregistre)="remplacer($event)"
      />
    }
  `,
  styles: `
    .aide {
      color: var(--mat-sys-on-surface-variant, #555);
    }
    .erreur {
      color: var(--mat-sys-error, #b00020);
    }
  `,
})
export class SectionCycles {
  private readonly service = inject(ServiceStructure);
  protected readonly droits = inject(Droits);

  protected readonly cycles = signal<CycleDto[]>([]);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      this.cycles.set(await this.service.cycles());
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected remplacer(modifie: CycleDto): void {
    this.cycles.update((liste) =>
      liste.map((c) => (c.id === modifie.id ? modifie : c)),
    );
  }
}
