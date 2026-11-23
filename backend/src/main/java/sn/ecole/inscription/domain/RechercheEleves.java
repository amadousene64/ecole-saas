package sn.ecole.inscription.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Recherche paginée des élèves (pagination par curseur sur {@code (nom_recherche, id)}). */
public interface RechercheEleves {

  /**
   * @param terme texte normalisé cherché dans « nom prénoms », ou matricule exact ; null : tous
   * @param matricule matricule exact (non nominatif) ; null : tous
   * @param classes élèves inscrits (pré-inscrits ou inscrits) dans l'une de ces classes (jamais
   *     vide) ; null : tous
   * @param statut élèves dont l'inscription la plus récente a ce statut ; null : tous
   * @param apresNom clé de tri du dernier élément de la page précédente, ou null
   * @param apresId identifiant du dernier élément de la page précédente, ou null
   * @param limite nombre maximal d'élèves renvoyés
   */
  List<Eleve> rechercher(
      String terme,
      String matricule,
      Collection<UUID> classes,
      StatutInscription statut,
      String apresNom,
      UUID apresId,
      int limite);
}
