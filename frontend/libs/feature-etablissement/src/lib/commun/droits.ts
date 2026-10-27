import { computed, inject, Service } from '@angular/core';
import { Session } from '@ecole/auth';

/**
 * Droits d'affichage dans l'école courante. C'est du confort d'interface (masquer ce qui serait
 * refusé) : le serveur contrôle chaque appel, quoi que l'interface montre.
 */
@Service()
export class Droits {
  private readonly session = inject(Session);

  /** DIRECTEUR et SECRETARIAT écrivent dans la structure de l'école. */
  readonly peutEcrire = computed(() =>
    this.session
      .roles()
      .some((role) => role === 'DIRECTEUR' || role === 'SECRETARIAT'),
  );

  /** Cachet, signature, ouverture/clôture d'année, cycles et bases de moyenne : directeur seul. */
  readonly estDirecteur = computed(() =>
    this.session.roles().includes('DIRECTEUR'),
  );
}
