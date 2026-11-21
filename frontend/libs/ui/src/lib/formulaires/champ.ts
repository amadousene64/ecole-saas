import {
  AfterContentInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  ElementRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { MatFormField } from '@angular/material/form-field';
import { MatSelect } from '@angular/material/select';

let suivant = 0;

/**
 * Champ de formulaire selon les maquettes (docs/maquettes/, 01 et 06) : libellé AU-DESSUS du champ,
 * jamais à l'intérieur ni sur sa bordure (pas de libellé flottant Material), 8 px entre les deux,
 * mention grise « (facultatif) » après le libellé d'un champ non obligatoire.
 *
 *   <ecole-champ libelle="E-mail">
 *     <mat-form-field appearance="outline">
 *       <input matInput formControlName="email" />
 *     </mat-form-field>
 *   </ecole-champ>
 *
 * Le `mat-form-field` projeté n'a pas de `mat-label` : Material dessine alors un contour plein, sans
 * encoche. Le libellé est un vrai `<label for>` relié au contrôle (clic, lecteurs d'écran) ; une
 * liste `mat-select`, qui n'est pas un élément de formulaire natif, est reliée par aria-labelledby.
 * Les classes de largeur (`champ-court`, `champ-moyen`, `champ-plein`) se posent sur `ecole-champ`.
 */
@Component({
  selector: 'ecole-champ',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="libelle-champ" [id]="idLibelle" [attr.for]="pour()">
      {{ libelle() }}
      @if (mentionAffichee(); as m) {
        <span class="mention">({{ m }})</span>
      }
    </label>
    <ng-content />
  `,
  styleUrl: './champ.scss',
})
export class Champ implements AfterContentInit {
  readonly libelle = input.required<string>();
  /**
   * Par défaut, « (facultatif) » s'affiche seul pour un champ de formulaire (relié à un contrôle)
   * qui n'est pas obligatoire ; un filtre (sans contrôle, ou dans un formulaire role=search) n'en a
   * pas. 'aucune' la retire.
   */
  readonly mention = input<'facultatif' | 'obligatoire' | 'aucune' | null>(
    null,
  );

  protected readonly idLibelle = `ecole-champ-${suivant++}`;
  protected readonly pour = signal<string | null>(null);
  private readonly facultatif = signal(false);
  protected readonly mentionAffichee = computed(() => {
    const m = this.mention();
    if (m === 'aucune') {
      return null;
    }
    return m ?? (this.facultatif() ? 'facultatif' : null);
  });
  private readonly champ = contentChild.required(MatFormField);
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);

  ngAfterContentInit(): void {
    const controle = this.champ()._control;
    const filtre = !!this.hote.nativeElement.closest('[role="search"]');
    this.facultatif.set(!filtre && !!controle.ngControl && !controle.required);
    if (controle instanceof MatSelect) {
      controle.ariaLabelledby = this.idLibelle;
    } else {
      this.pour.set(controle.id);
    }
  }
}
