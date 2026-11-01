package sn.ecole.shared.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de l'outbox ; toujours limité à l'école courante. */
public interface OutboxEventRepository {

  OutboxEvent save(OutboxEvent evenement);

  List<OutboxEvent> findTop100ByTraiteLeIsNullOrderByCreeLeAsc();

  /**
   * Identifiants des événements à traiter maintenant : non traités, sous le plafond de tentatives,
   * dont la prochaine tentative est échue ; du plus ancien au plus récent.
   */
  List<UUID> aTraiter(Instant maintenant, int tentativesMax, int limite);

  /**
   * Verrouille l'événement s'il est encore à traiter ; vide s'il est déjà traité ou verrouillé par
   * un autre relais ({@code SKIP LOCKED}) : deux instances ne le traitent jamais en même temps.
   */
  Optional<OutboxEvent> verrouiller(UUID id);
}
