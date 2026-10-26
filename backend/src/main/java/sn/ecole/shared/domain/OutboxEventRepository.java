package sn.ecole.shared.domain;

import java.util.List;

/** Port de l'outbox ; toujours limité à l'école courante. */
public interface OutboxEventRepository {

  OutboxEvent save(OutboxEvent evenement);

  List<OutboxEvent> findTop100ByTraiteLeIsNullOrderByCreeLeAsc();
}
