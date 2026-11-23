package sn.ecole.shared.application;

import org.springframework.stereotype.Component;
import sn.ecole.shared.domain.CacheRoles;
import sn.ecole.shared.domain.OutboxEvent;

/**
 * Rejoue l'invalidation des droits d'un utilisateur ({@link InvalidationDroits#auCommitSansRedis})
 * : tant que Redis est injoignable, l'exception fait réessayer le relais plus tard. Idempotent :
 * une génération de plus ne coûte qu'une relecture en base.
 */
@Component
class InvalidationDroitsOutbox implements TraitementOutbox {

  private final CacheRoles cache;

  InvalidationDroitsOutbox(CacheRoles cache) {
    this.cache = cache;
  }

  @Override
  public String typeEvenement() {
    return InvalidationDroits.A_INVALIDER;
  }

  @Override
  public void traiter(OutboxEvent evenement) {
    cache.nouvelleGeneration(evenement.getIdAgregat());
  }
}
