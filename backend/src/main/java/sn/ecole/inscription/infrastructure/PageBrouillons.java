package sn.ecole.inscription.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import sn.ecole.inscription.domain.BrouillonInscription;

/**
 * Fragment Spring Data : pagination « keyset » sur (modifieLe, id), du plus récent au plus ancien.
 */
interface PageBrouillons {

  List<BrouillonInscription> page(Instant apresModifieLe, UUID apresId, int limite);

  void verrouillerAuteur(UUID auteurId);
}
