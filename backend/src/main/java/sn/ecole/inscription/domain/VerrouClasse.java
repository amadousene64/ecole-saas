package sn.ecole.inscription.domain;

import java.util.UUID;

/**
 * Verrou de transaction sur une classe : sérialise les inscriptions dans une même classe (contrôle
 * de capacité) et la suppression ou le changement de niveau de la classe (issue #3).
 */
public interface VerrouClasse {

  /** Bloque jusqu'à obtenir le verrou ; libéré à la fin de la transaction. */
  void verrouiller(UUID classeId);
}
