import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { Api, activationActiver } from '@ecole/api-client';
import { EcoleCourante, Session } from '@ecole/auth';

/** Vrai sur localhost ou une adresse IP : il n'y a pas de sous-domaine d'école pour la désigner. */
export function sansSousDomaine(hote = window.location.hostname): boolean {
  return hote === 'localhost' || /^\d{1,3}(\.\d{1,3}){3}$/.test(hote);
}

/** Saisie tolérée comme côté serveur : majuscules, sans espaces ni tirets superflus. */
export function normaliserCode(saisie: string): string {
  return saisie.toUpperCase().replace(/[^0-9A-Z]/g, '');
}

/** Présentation XXXX-XXXX-XXXX (12 caractères), pour la lisibilité pendant la saisie. */
export function presenterCode(brut: string): string {
  return (
    normaliserCode(brut)
      .slice(0, 12)
      .match(/.{1,4}/g)
      ?.join('-') ?? ''
  );
}

/**
 * Activation du compte avec le code remis par l'école. Les refus sont volontairement génériques
 * (code inconnu, expiré ou déjà utilisé se ressemblent) pour ne pas aider à deviner un code ; les
 * essais sont limités par le serveur.
 */
@Component({
  selector: 'ecole-activation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Activer mon compte</h1>
    <p class="texte-secondaire">
      Saisissez le code d'activation remis par l'école de votre enfant.
    </p>
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-activation">
        {{ message }}
      </p>
    }
    <form
      class="carte"
      (submit)="activer($event)"
      data-testid="form-activation"
    >
      @if (champEcole()) {
        <div class="champ">
          <label for="ecole-dev">Identifiant de l'école (développement)</label>
          <input
            id="ecole-dev"
            type="text"
            autocomplete="off"
            [value]="ecole()"
            (input)="ecole.set($any($event.target).value)"
            data-testid="champ-ecole"
          />
        </div>
      }
      <div class="champ">
        <label for="code">Code d'activation</label>
        <input
          id="code"
          class="mono"
          type="text"
          autocomplete="off"
          autocapitalize="characters"
          spellcheck="false"
          maxlength="14"
          aria-describedby="aide-code"
          [value]="code()"
          (input)="saisir($any($event.target).value)"
          data-testid="champ-code"
        />
        <p id="aide-code" class="texte-secondaire">
          12 caractères, par exemple 7K2M-X9QD-4RTB.
        </p>
      </div>
      <button
        type="submit"
        class="bouton"
        [disabled]="occupe() || !complet()"
        data-testid="activer"
      >
        Activer mon compte
      </button>
    </form>
  `,
  styles: `
    .champ {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin-bottom: 16px;
    }
    input {
      font: inherit;
      padding: 10px 12px;
      border: 1px solid var(--ecole-bordure-champ);
      border-radius: 8px;
      background: var(--ecole-surface);
      color: inherit;
      letter-spacing: 0.08em;
    }
  `,
})
export class PageActivation {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  private readonly courante = inject(EcoleCourante);
  private readonly routeur = inject(Router);

  protected readonly code = signal('');
  protected readonly ecole = signal('');
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  /**
   * En production l'école vient du sous-domaine, côté serveur. Sur un poste de développement
   * (localhost, adresse IP), sans école mémorisée, on la désigne à la main.
   */
  protected readonly champEcole = signal(
    !this.courante.id() && sansSousDomaine(),
  );

  protected complet(): boolean {
    return normaliserCode(this.code()).length === 12;
  }

  protected saisir(valeur: string): void {
    this.code.set(presenterCode(valeur));
  }

  protected async activer(evenement: Event): Promise<void> {
    evenement.preventDefault();
    if (!this.complet() || this.occupe()) {
      return;
    }
    if (this.champEcole() && this.ecole().trim()) {
      this.courante.choisir(this.ecole().trim());
    }
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      await this.api.invoke(activationActiver, {
        body: { code: normaliserCode(this.code()) },
      });
      // Le profil dans l'école n'existait pas : on le recharge avant de montrer les enfants.
      this.code.set('');
      await this.session.recharger();
      await this.routeur.navigateByUrl('/enfants');
    } catch (e) {
      this.erreur.set(message(e));
    } finally {
      this.occupe.set(false);
    }
  }
}

function message(erreur: unknown): string {
  if (erreur instanceof HttpErrorResponse) {
    if (erreur.status === 429) {
      return 'Trop d’essais. Patientez quelques minutes avant de réessayer.';
    }
    if (erreur.status === 0) {
      return 'Le serveur est injoignable. Vérifiez votre connexion.';
    }
  }
  return 'Ce code n’est pas valable. Vérifiez-le, ou demandez-en un nouveau à l’école.';
}
