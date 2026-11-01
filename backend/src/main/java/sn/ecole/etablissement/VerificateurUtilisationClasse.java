package sn.ecole.etablissement;

import java.util.UUID;

/**
 * Port implémenté par les modules qui rattachent des données à une classe (inscription, plus tard
 * évaluation) : une classe utilisée ne peut plus être supprimée, ni changer de niveau ou de série,
 * ni retirer une option choisie par un élève. Sans cela, supprimer puis recréer la classe sur un
 * autre niveau contournerait le figement de l'année (issue #3).
 *
 * <p>Appelé dans la transaction de modification de la classe. L'implémentation prend le même verrou
 * que la création d'une inscription dans cette classe : les deux opérations ne peuvent pas se
 * croiser.
 */
public interface VerificateurUtilisationClasse {

  /** Vrai si au moins une donnée (inscription, quel que soit son statut) référence la classe. */
  boolean classeUtilisee(UUID classeId);

  /** Vrai si au moins un élève de la classe a choisi cette option. */
  boolean optionUtilisee(UUID classeId, UUID optionId);
}
