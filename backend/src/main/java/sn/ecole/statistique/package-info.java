/**
 * Statistiques de la direction : effectifs, état des inscriptions, dossiers et familles connectées,
 * courbe des inscriptions. Aucune table propre : les agrégats sont demandés aux autres modules par
 * leur API publique ({@code StatistiquesInscription}, {@code ReferentielScolaire}) et gardés cinq
 * minutes dans Redis, par école.
 *
 * <p>Que des nombres : ce module ne manipule jamais d'élève, de nom ni de coordonnée.
 */
@ApplicationModule(displayName = "Statistique")
package sn.ecole.statistique;

import org.springframework.modulith.ApplicationModule;
