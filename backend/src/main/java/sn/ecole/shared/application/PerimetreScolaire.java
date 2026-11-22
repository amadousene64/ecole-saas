package sn.ecole.shared.application;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

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

  /** Refus (403) si l'utilisateur ne voit pas la classe. */
  default void exigerClasse(UUID classeId) {
    if (!voitClasse(classeId)) {
      throw new AccessDeniedException("Classe hors de votre périmètre.");
    }
  }
}
