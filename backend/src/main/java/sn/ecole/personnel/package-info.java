/**
 * Personnel de l'école : annuaire (noms tirés du compte à la connexion) et affectations par année
 * scolaire (enseignant ↔ classe × matière, maître titulaire, surveillant ↔ cycles).
 *
 * <p>Implémente {@link sn.ecole.shared.application.PerimetreScolaire} : les classes que voit chaque
 * utilisateur, refus par défaut. Les autres modules l'utilisent par cette interface du socle, sans
 * dépendre de celui-ci.
 *
 * <p>API publique du module : les types de ce package. Tout le reste est interne.
 */
@ApplicationModule(displayName = "Personnel")
package sn.ecole.personnel;

import org.springframework.modulith.ApplicationModule;
