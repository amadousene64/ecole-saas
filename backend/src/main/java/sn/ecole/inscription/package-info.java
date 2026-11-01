/**
 * Élèves, responsables et liens parentaux, inscriptions annuelles et pièces du dossier.
 *
 * <p>Premier module à porter des données personnelles de mineurs : minimisation, moindre privilège
 * et traçabilité priment. Les données de santé et la situation familiale sont chiffrées en colonne
 * et chaque lecture est journalisée ; aucun nom, téléphone ni donnée de santé n'est écrit dans les
 * journaux applicatifs.
 *
 * <p>API publique du module : les types de ce package. Tout le reste est interne.
 */
@ApplicationModule(displayName = "Inscription")
package sn.ecole.inscription;

import org.springframework.modulith.ApplicationModule;
