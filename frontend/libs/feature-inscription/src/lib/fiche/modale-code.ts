import {
  Component,
  inject,
  input,
  LOCALE_ID,
  output,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Modale } from '@ecole/ui';

export interface DonneesCode {
  /** À qui le code est remis (nom du responsable ou de l'élève). */
  titulaire: string;
  code: string;
  expireLe?: string;
}

/**
 * Code d'activation : affiché UNE fois, avec copie et impression. Le parent crée cette modale
 * quand le serveur renvoie un code et la DÉTRUIT à la fermeture (`@if`) : le code ne reste nulle
 * part dans la page (le serveur ne le garde pas en clair et ne sait plus le redonner).
 *
 * Tant qu'il n'est ni copié ni imprimé, Échap, « × » et le fond demandent confirmation ; le bouton
 * « Fermer » ferme directement (l'avertissement est écrit dans la modale).
 */
@Component({
  selector: 'ecole-modale-code',
  imports: [Modale, MatButtonModule],
  // Styles d'impression globaux : n'imprimer que le code.
  encapsulation: ViewEncapsulation.None,
  template: `
    <ecole-modale
      titre="Code d'activation"
      testId="modale-code"
      focusInitial="[data-testid=copier-code]"
      [ouverte]="true"
      [modifiee]="!conserve()"
      messageAbandon="Ce code ne sera plus jamais affiché. Fermer sans l'avoir copié ni imprimé ?"
      libelleContinuer="Revenir au code"
      libelleAbandon="Fermer sans copier"
      (fermee)="fermee.emit()"
    >
      <div class="code-activation">
        <p>
          Code à remettre à <strong>{{ donnees().titulaire }}</strong
          >.
        </p>
        <p
          class="code-valeur mono"
          data-testid="code-valeur"
          aria-label="Code d'activation"
        >
          {{ donnees().code }}
        </p>
        @if (donnees().expireLe) {
          <p class="aide">Valable jusqu'au {{ expiration() }}.</p>
        }
        <p class="message ok" role="note">
          Ce code ne sera plus jamais affiché. Copiez-le ou imprimez-le avant de
          fermer cette fenêtre ; sinon, il faudra en générer un nouveau.
        </p>
        <p class="sr-only" role="status" aria-live="polite">{{ copie() }}</p>
      </div>
      <div pied>
        <button
          matButton
          type="button"
          (click)="copier()"
          data-testid="copier-code"
        >
          Copier
        </button>
        <button
          matButton
          type="button"
          (click)="imprimer()"
          data-testid="imprimer-code"
        >
          Imprimer
        </button>
        <button
          matButton="filled"
          type="button"
          (click)="fermee.emit()"
          data-testid="fermer-code"
        >
          Fermer
        </button>
      </div>
    </ecole-modale>
  `,
  styleUrl: './modale-code.scss',
})
export class ModaleCode {
  readonly donnees = input.required<DonneesCode>();
  /** Fermeture : le parent détruit la modale, et le code avec. */
  readonly fermee = output<void>();

  private readonly locale = inject(LOCALE_ID);
  protected readonly copie = signal('');
  /** Copié ou imprimé : la fermeture n'a plus à être confirmée. */
  protected readonly conserve = signal(false);

  protected expiration(): string {
    return new Date(this.donnees().expireLe ?? '').toLocaleDateString(
      this.locale,
      { dateStyle: 'long' },
    );
  }

  protected async copier(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.donnees().code);
      this.copie.set('Code copié dans le presse-papiers.');
      this.conserve.set(true);
    } catch {
      this.copie.set('Copie impossible : sélectionnez le code à la main.');
    }
  }

  protected imprimer(): void {
    this.conserve.set(true);
    window.print();
  }
}
