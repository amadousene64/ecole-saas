package sn.ecole.shared.infrastructure;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import sn.ecole.shared.domain.OutboxEvent;
import sn.ecole.shared.domain.OutboxEventRepository;

/** Implémentation Spring Data du port {@link OutboxEventRepository}. */
interface OutboxEventJpaRepository extends Repository<OutboxEvent, UUID>, OutboxEventRepository {

  @Query(
      """
      select e.id from OutboxEvent e
      where e.traiteLe is null and e.tentatives < :max and e.prochaineTentativeLe <= :maintenant
      order by e.creeLe""")
  List<UUID> aTraiter(
      @Param("maintenant") Instant maintenant, @Param("max") int tentativesMax, Limit limite);

  @Override
  default List<UUID> aTraiter(Instant maintenant, int tentativesMax, int limite) {
    return aTraiter(maintenant, tentativesMax, Limit.of(limite));
  }

  /** -2 : {@code SKIP LOCKED} pour Hibernate. */
  @Override
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query("select e from OutboxEvent e where e.id = :id and e.traiteLe is null")
  Optional<OutboxEvent> verrouiller(@Param("id") UUID id);
}
