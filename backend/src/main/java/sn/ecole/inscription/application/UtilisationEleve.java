package sn.ecole.inscription.application;

import java.util.UUID;

/**
 * Données rattachées à un élève (inscriptions, liens parentaux, pièces) : un élève qui en a ne peut
 * plus être supprimé. Chaque partie du module qui rattache des données en fournit une.
 */
interface UtilisationEleve {

  boolean eleveUtilise(UUID eleveId);
}
