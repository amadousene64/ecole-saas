package sn.ecole.shared.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.shared.domain.OutboxEvent;
import sn.ecole.shared.domain.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Écrit un événement dans {@code outbox_event}, dans la transaction métier en cours (obligatoire) :
 * l'événement existe si et seulement si le changement métier est validé.
 */
@Component
public class OutboxPublisher {

  private final OutboxEventRepository outbox;
  private final JsonMapper json;

  public OutboxPublisher(OutboxEventRepository outbox, JsonMapper json) {
    this.outbox = outbox;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public OutboxEvent publier(
      String typeAgregat, UUID idAgregat, String typeEvenement, Object charge) {
    return outbox.save(
        new OutboxEvent(typeAgregat, idAgregat, typeEvenement, json.writeValueAsString(charge)));
  }
}
