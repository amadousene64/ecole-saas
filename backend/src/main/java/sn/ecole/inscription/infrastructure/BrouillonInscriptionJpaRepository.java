package sn.ecole.inscription.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import sn.ecole.inscription.domain.BrouillonInscription;
import sn.ecole.inscription.domain.BrouillonInscriptionRepository;

/** Implémentation Spring Data du port {@link BrouillonInscriptionRepository}. */
interface BrouillonInscriptionJpaRepository
    extends Repository<BrouillonInscription, UUID>, BrouillonInscriptionRepository, PageBrouillons {

  @Override
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BrouillonInscription b where b.id = :id")
  Optional<BrouillonInscription> verrouiller(@Param("id") UUID id);

  @Override
  @Query("select b.id from BrouillonInscription b where b.modifieLe < :avant")
  List<UUID> expiresAvant(@Param("avant") Instant avant);
}
