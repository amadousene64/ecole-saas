import { inject, Service } from '@angular/core';
import { Acces } from '@ecole/auth';

/**
 * Droits d'affichage dans l'école courante, tirés de la table commune (`@ecole/auth`) comme le menu
 * et les gardes de route. C'est du confort d'interface (masquer ce qui serait refusé) : le
 * serveur contrôle chaque appel, quoi que l'interface montre.
 */
@Service()
export class Droits {
  private readonly acces = inject(Acces);

  /** DIRECTEUR et SECRETARIAT écrivent (identité, responsables, pièces, inscriptions). */
  readonly peutEcrire = this.acces.signalPour('eleves.ecrire');

  readonly peutCreer = this.acces.signalPour('eleves.creer');

  readonly peutImporter = this.acces.signalPour('eleves.importer');

  /** Accès d'un responsable à l'espace parent : directeur seul. */
  readonly estDirecteur = this.acces.signalPour('eleves.acces-parent');

  /** Radiation : directeur seul. */
  readonly peutRadier = this.acces.signalPour('inscription.radier');

  /** Lecture du contenu des données sensibles : DIRECTEUR, CENSEUR. */
  readonly peutLireSensible = this.acces.signalPour('eleves.sensible.lire');

  /** « Renseigné / non renseigné » des données sensibles. */
  readonly voitEtatSensible = this.acces.signalPour('eleves.sensible.etat');

  readonly voitPieces = this.acces.signalPour('eleves.pieces.voir');

  readonly peutOuvrirCertificat = this.acces.signalPour(
    'eleves.certificat.ouvrir',
  );

  readonly voitScolarite = this.acces.signalPour('eleves.scolarite.voir');

  readonly voitResponsables = this.acces.signalPour('eleves.responsables.voir');
}
