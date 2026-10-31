import { computed, inject, Service } from '@angular/core';
import { Session } from '@ecole/auth';

/**
 * Droits d'affichage dans l'école courante. C'est du confort d'interface (masquer ce qui serait
 * refusé) : le serveur contrôle chaque appel, quoi que l'interface montre.
 */
@Service()
export class Droits {
  private readonly session = inject(Session);

  private a(...roles: string[]): boolean {
    return this.session.roles().some((r) => roles.includes(r));
  }

  /** DIRECTEUR et SECRETARIAT écrivent (identité, responsables, pièces, inscriptions). */
  readonly peutEcrire = computed(() => this.a('DIRECTEUR', 'SECRETARIAT'));

  /** Accès à l'espace parent d'un lien, radiation : directeur seul. */
  readonly estDirecteur = computed(() => this.a('DIRECTEUR'));

  /** Lecture du contenu des données sensibles (santé, situation familiale) : DIRECTEUR, CENSEUR. */
  readonly peutLireSensible = computed(() => this.a('DIRECTEUR', 'CENSEUR'));

  /** Le secrétariat voit seulement « renseigné / non renseigné » et peut saisir. */
  readonly voitEtatSensible = computed(() => this.a('SECRETARIAT'));

  /** Pièces du dossier visibles : direction, secrétariat, censeur (certificat médical seul). */
  readonly voitPieces = computed(() =>
    this.a('DIRECTEUR', 'SECRETARIAT', 'CENSEUR'),
  );

  /** Ouverture du certificat médical : DIRECTEUR et CENSEUR. */
  readonly peutOuvrirCertificat = computed(() =>
    this.a('DIRECTEUR', 'CENSEUR'),
  );

  /** Scolarité (inscriptions) : sans le comptable. */
  readonly voitScolarite = computed(() =>
    this.a('DIRECTEUR', 'SECRETARIAT', 'CENSEUR', 'SURVEILLANT'),
  );

  /** Responsables et leurs liens : direction, secrétariat, comptable (payeurs). */
  readonly voitResponsables = computed(() =>
    this.a('DIRECTEUR', 'SECRETARIAT', 'COMPTABLE'),
  );
}
