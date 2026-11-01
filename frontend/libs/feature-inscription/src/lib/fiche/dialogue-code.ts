import { Component, inject, signal, ViewEncapsulation } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export interface DonneesCode {
  /** À qui le code est remis (nom du responsable ou de l'élève). */
  titulaire: string;
  code: string;
  expireLe?: string;
}

/**
 * Code d'activation : affiché UNE fois, avec copie et impression. Le code ne vit que dans les
 * données de ce dialogue ; une fois fermé, il n'est plus nulle part (le serveur ne le garde pas
 * en clair et ne sait plus le redonner).
 */
@Component({
  selector: 'ecole-dialogue-code',
  imports: [MatDialogModule, MatButtonModule],
  encapsulation: ViewEncapsulation.None,
  template: `
    <h2 mat-dialog-title>Code d'activation</h2>
    <mat-dialog-content class="code-activation">
      <p>
        Code à remettre à <strong>{{ donnees.titulaire }}</strong
        >.
      </p>
      <p
        class="code-valeur mono"
        data-testid="code-valeur"
        aria-label="Code d'activation"
      >
        {{ donnees.code }}
      </p>
      @if (donnees.expireLe) {
        <p class="aide">Valable jusqu'au {{ expiration() }}.</p>
      }
      <p class="message ok" role="note">
        Ce code ne sera plus jamais affiché. Copiez-le ou imprimez-le avant de
        fermer cette fenêtre ; sinon, il faudra en générer un nouveau.
      </p>
      <p class="sr-only" role="status" aria-live="polite">{{ copie() }}</p>
    </mat-dialog-content>
    <mat-dialog-actions>
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
        mat-dialog-close
        data-testid="fermer-code"
      >
        Fermer
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .code-activation .code-valeur {
      font-size: 1.75rem;
      letter-spacing: 0.12em;
      margin: 12px 0;
      user-select: all;
    }
    @media print {
      body > *:not(.cdk-overlay-container) {
        display: none !important;
      }
      .cdk-overlay-backdrop {
        display: none !important;
      }
      .cdk-overlay-pane {
        position: static !important;
        max-width: none !important;
      }
      .mat-mdc-dialog-actions {
        display: none !important;
      }
    }
  `,
})
export class DialogueCode {
  protected readonly donnees = inject<DonneesCode>(MAT_DIALOG_DATA);
  protected readonly copie = signal('');

  protected expiration(): string {
    return new Date(this.donnees.expireLe ?? '').toLocaleDateString('fr-FR', {
      dateStyle: 'long',
    });
  }

  protected async copier(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.donnees.code);
      this.copie.set('Code copié dans le presse-papiers.');
    } catch {
      this.copie.set('Copie impossible : sélectionnez le code à la main.');
    }
  }

  protected imprimer(): void {
    window.print();
  }
}
