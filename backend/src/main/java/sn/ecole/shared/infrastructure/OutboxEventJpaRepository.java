package sn.ecole.shared.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.shared.domain.OutboxEvent;
import sn.ecole.shared.domain.OutboxEventRepository;

/** Implémentation Spring Data du port {@link OutboxEventRepository}. */
interface OutboxEventJpaRepository extends Repository<OutboxEvent, UUID>, OutboxEventRepository {}
