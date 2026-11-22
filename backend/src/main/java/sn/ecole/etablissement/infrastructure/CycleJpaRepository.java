package sn.ecole.etablissement.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;

/** Implémentation Spring Data du port {@link CycleRepository}. */
interface CycleJpaRepository extends Repository<Cycle, UUID>, CycleRepository {

  @Override
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Cycle c where c.id = :id")
  Optional<Cycle> verrouiller(@Param("id") UUID id);
}
