import { inject, Service } from '@angular/core';
import { Acces } from '@ecole/auth';

/**
 * Droits d'affichage dans l'école courante, tirés de la table commune (`@ecole/auth`) comme le menu
 * et les gardes de route. C'est du confort d'interface : le serveur contrôle chaque appel.
 */
@Service()
export class Droits {
  private readonly acces = inject(Acces);

  /** DIRECTEUR et SECRETARIAT écrivent dans la structure de l'école. */
  readonly peutEcrire = this.acces.signalPour('etablissement.ecrire');

  /** Cachet, signature, ouverture/clôture d'année, cycles et bases de moyenne : directeur seul. */
  readonly estDirecteur = this.acces.signalPour('etablissement.direction');
}
