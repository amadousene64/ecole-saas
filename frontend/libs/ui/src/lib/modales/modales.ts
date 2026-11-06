import { DOCUMENT, Injectable, inject, isDevMode, signal } from '@angular/core';

/** Classe posée sur <html> tant qu'une modale est ouverte (défilement de la page bloqué). */
export const CLASSE_MODALE_OUVERTE = 'modale-ouverte';

/**
 * Registre des modales : JAMAIS deux modales empilées. Une modale (`ecole-modale`,
 * `ecole-confirmation`) réserve la place avant de s'ouvrir et la libère en se fermant ; une
 * seconde demande est refusée (et signalée en développement). Pour enchaîner deux étapes, fermer
 * la première avant d'ouvrir la seconde (ex. : retrait d'un responsable depuis sa modale).
 */
@Injectable({ providedIn: 'root' })
export class Modales {
  private readonly document = inject(DOCUMENT);
  private proprietaire: object | null = null;
  private readonly _ouverte = signal(false);

  /** Une modale est ouverte. */
  readonly ouverte = this._ouverte.asReadonly();

  /** Réserve la place pour `proprietaire` ; faux si une AUTRE modale est déjà ouverte. */
  reserver(proprietaire: object): boolean {
    if (this.proprietaire && this.proprietaire !== proprietaire) {
      if (isDevMode()) {
        console.error(
          'ecole-modale : une modale est déjà ouverte, la seconde est refusée (jamais deux modales empilées).',
        );
      }
      return false;
    }
    this.proprietaire = proprietaire;
    this._ouverte.set(true);
    this.document.documentElement.classList.add(CLASSE_MODALE_OUVERTE);
    return true;
  }

  /** Libère la place si `proprietaire` la tient (sans effet sinon). */
  liberer(proprietaire: object): void {
    if (this.proprietaire !== proprietaire) {
      return;
    }
    this.proprietaire = null;
    this._ouverte.set(false);
    this.document.documentElement.classList.remove(CLASSE_MODALE_OUVERTE);
  }
}
