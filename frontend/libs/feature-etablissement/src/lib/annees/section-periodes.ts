import {
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { requis } from '../commun/requis';
import { AnneeScolaireDto, CycleDto, PeriodeDto } from '@ecole/api-client';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { LIBELLES_CYCLE } from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceAnnees } from '../services/service-annees';
import { ServiceStructure } from '../services/service-structure';
import { LignePeriode } from './ligne-periode';

/** Périodes d'une année, par cycle. Ajustables tant que l'année n'est pas clôturée. */
@Component({
  selector: 'ecole-section-periodes',
  imports: [MatButtonModule, LignePeriode],
  template: `
    <h3>Périodes</h3>
    @if (erreur(); as message) {
      <p class="message erreur" role="alert">{{ message }}</p>
    }
    @for (groupe of groupes(); track groupe.cycle.id) {
      <h4>{{ groupe.nom }}</h4>
      <div
        class="periodes"
        role="group"
        [attr.aria-label]="'Périodes : ' + groupe.nom"
        [attr.data-testid]="'periodes-' + groupe.cycle.code"
      >
        @for (p of groupe.periodes; track p.id) {
          <ecole-ligne-periode
            [periode]="p"
            [modifiable]="modifiable()"
            (modifiee)="remplacer($event)"
          />
        }
      </div>
    }
    @if (droits.peutEcrire() && annee().statut === 'PREPARATION') {
      <button
        matButton
        type="button"
        (click)="regenerer()"
        data-testid="regenerer-periodes"
      >
        Régénérer les périodes du modèle
      </button>
      <p class="aide">
        Remplace les périodes par un découpage égal selon le type de période de
        chaque cycle.
      </p>
    }
  `,
  styles: [
    STYLES_COMMUNS,
    `
      .periodes {
        margin-bottom: 16px;
      }
    `,
  ],
})
export class SectionPeriodes {
  private readonly service = inject(ServiceAnnees);
  private readonly structure = inject(ServiceStructure);
  protected readonly droits = inject(Droits);

  readonly annee = input.required<AnneeScolaireDto>();

  protected readonly periodes = signal<PeriodeDto[]>([]);
  protected readonly cycles = signal<CycleDto[]>([]);
  protected readonly erreur = signal<string | null>(null);

  protected readonly modifiable = computed(
    () => this.droits.peutEcrire() && this.annee().statut !== 'CLOTUREE',
  );

  protected readonly groupes = computed(() =>
    this.cycles().map((cycle) => ({
      cycle,
      nom: LIBELLES_CYCLE[cycle.code ?? ''] ?? cycle.libelle ?? '',
      periodes: this.periodes()
        .filter((p) => p.cycleId === cycle.id)
        .sort((a, b) => (a.numero ?? 0) - (b.numero ?? 0)),
    })),
  );

  constructor() {
    effect(() => {
      void this.charger(requis(this.annee().id));
    });
  }

  private async charger(anneeId: string): Promise<void> {
    try {
      const [periodes, cycles] = await Promise.all([
        this.service.periodes(anneeId),
        this.structure.cycles(),
      ]);
      this.periodes.set(periodes);
      this.cycles.set(cycles);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected remplacer(modifiee: PeriodeDto): void {
    this.periodes.update((liste) =>
      liste.map((p) => (p.id === modifiee.id ? modifiee : p)),
    );
  }

  protected async regenerer(): Promise<void> {
    this.erreur.set(null);
    try {
      this.periodes.set(
        await this.service.genererPeriodes(requis(this.annee().id)),
      );
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }
}
