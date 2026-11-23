package sn.ecole.shared.application;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Périmètre de l'utilisateur connecté dans l'école courante : quelles classes il voit, aujourd'hui.
 * Implémenté par le module {@code personnel} (affectations) ; tout endpoint qui concerne des élèves
 * ou des classes passe par lui. Refus par défaut :
 *
 * <ul>
 *   <li>direction, censeur, secrétariat, comptabilité : toute l'école ;
 *   <li>enseignant : les classes où il a une affectation en cours (matière ou titulaire) ;
 *   <li>surveillant : les classes des cycles qui lui sont affectés ;
 *   <li>sans affectation, ou aucun rôle du personnel : aucune classe.
 * </ul>
 *
 * Un parent ou un élève n'y passe pas : son accès se vérifie élève par élève ({@code
 * hasPermission(#eleveId, 'Eleve', 'lire')}).
 */
public interface PerimetreScolaire {

  /** Vrai si l'utilisateur voit toute l'école (aucun filtre de classe). */
  boolean toutLEcole();

  /** Vrai si l'utilisateur voit cette classe (toujours vrai pour {@link #toutLEcole()}). */
  boolean voitClasse(UUID classeId);

  /** Les classes de {@code classeIds} que l'utilisateur voit, dans l'ordre reçu. */
  Set<UUID> filtrer(Collection<UUID> classeIds);

  /**
   * Refus (403 HORS_PERIMETRE) si l'utilisateur ne voit pas la classe. Même réponse pour une classe
   * inexistante, d'une autre école ou hors périmètre : rien ne laisse deviner qu'elle existe.
   */
  default void exigerClasse(UUID classeId) {
    if (!voitClasse(classeId)) {
      throw horsPerimetre();
    }
  }

  /**
   * Le refus de périmètre, identique partout (classe, élève) : 403, code HORS_PERIMETRE, message
   * fixe sans identifiant. Distinct d'ACCES_REFUSE (rôle insuffisant).
   */
  static ErreurMetier horsPerimetre() {
    return new ErreurMetier(
        "HORS_PERIMETRE", TypeErreur.INTERDIT, "Hors de votre périmètre d'affectations.");
  }
}
