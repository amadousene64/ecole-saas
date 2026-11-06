import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  model,
  output,
  signal,
  untracked,
} from '@angular/core';
import { Modale } from './modale';

let suivant = 0;

/**
 * Confirmation d'une action lourde (radiation, clôture, retrait) : `alertdialog` sur
 * `ecole-modale`. Une icône, la CONSÉQUENCE écrite en clair, un motif obligatoire si demandé, et
 * un bouton rouge nommé par l'action (« Radier l'élève », jamais « OK »). Le focus s'ouvre sur
 * « Annuler » : une validation par réflexe ne déclenche rien.
 *
 * `confirmee` émet le motif (ou `undefined`) ; le parent exécute l'action, affiche une éventuelle
 * erreur par `[erreur]` (la confirmation reste ouverte) et remet `ouverte` à faux une fois fait.
 */
@Component({
  selector: 'ecole-confirmation',
  imports: [Modale],
  templateUrl: './confirmation.html',
  styleUrl: './confirmation.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Confirmation {
  readonly titre = input.required<string>();
  /** Ce qui va se passer, en une ou deux phrases. */
  readonly consequence = input.required<string>();
  /** Libellé du bouton rouge : l'action elle-même. */
  readonly action = input.required<string>();
  readonly motif = input<'aucun' | 'obligatoire' | 'facultatif'>('aucun');
  readonly libelleMotif = input('Motif');
  readonly motifInitial = input('');
  readonly occupe = input(false);
  readonly erreur = input<string | null>(null);
  readonly testId = input<string>();
  readonly ouverte = model(false);

  readonly confirmee = output<string | undefined>();
  readonly annulee = output<void>();

  protected readonly id = `ecole-confirmation-${++suivant}`;
  protected readonly saisie = signal('');
  protected readonly tentee = signal(false);
  protected readonly motifManquant = computed(
    () => this.motif() === 'obligatoire' && this.saisie().trim().length === 0,
  );

  constructor() {
    // Chaque ouverture repart du motif proposé, sans erreur affichée.
    effect(() => {
      if (this.ouverte()) {
        const initial = this.motifInitial();
        untracked(() => {
          this.saisie.set(initial);
          this.tentee.set(false);
        });
      }
    });
  }

  protected confirmer(): void {
    this.tentee.set(true);
    if (this.occupe() || this.motifManquant()) {
      return;
    }
    const motif = this.saisie().trim();
    this.confirmee.emit(this.motif() === 'aucun' || !motif ? undefined : motif);
  }

  protected annuler(): void {
    this.ouverte.set(false);
    this.annulee.emit();
  }
}
