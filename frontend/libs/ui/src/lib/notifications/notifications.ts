import {
  ChangeDetectionStrategy,
  Component,
  inject,
  Service,
  signal,
} from '@angular/core';

export type TonNotification = 'info' | 'succes' | 'erreur';

export interface Notification {
  id: number;
  texte: string;
  ton: TonNotification;
}

const DUREE_MS = 8000;

/** Notifications courtes, affichées en bas de l'écran par {@link ZoneNotifications}. */
@Service()
export class Notifications {
  private compteur = 0;
  readonly liste = signal<readonly Notification[]>([]);

  info(texte: string): void {
    this.afficher(texte, 'info');
  }

  succes(texte: string): void {
    this.afficher(texte, 'succes');
  }

  erreur(texte: string): void {
    this.afficher(texte, 'erreur');
  }

  fermer(id: number): void {
    this.liste.update((l) => l.filter((n) => n.id !== id));
  }

  private afficher(texte: string, ton: TonNotification): void {
    const id = ++this.compteur;
    // Une même phrase déjà affichée n'est pas empilée une seconde fois.
    this.liste.update((l) => [
      ...l.filter((n) => n.texte !== texte),
      { id, texte, ton },
    ]);
    setTimeout(() => this.fermer(id), DUREE_MS);
  }
}

/** Zone en bas de l'écran (une seule par application), annoncée aux lecteurs d'écran. */
@Component({
  selector: 'ecole-zone-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="zone" role="status" aria-live="polite" aria-atomic="false">
      @for (n of notifications.liste(); track n.id) {
        <p
          class="notification"
          [class]="n.ton"
          [attr.data-testid]="'notification-' + n.ton"
        >
          <span>{{ n.texte }}</span>
          <button
            type="button"
            class="fermer"
            (click)="notifications.fermer(n.id)"
          >
            Fermer<span class="sr-only"> la notification</span>
          </button>
        </p>
      }
    </div>
  `,
  styleUrl: './notifications.scss',
})
export class ZoneNotifications {
  protected readonly notifications = inject(Notifications);
}
