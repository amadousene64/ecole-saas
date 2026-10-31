package sn.ecole.inscription.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import sn.ecole.inscription.domain.CodeActivation;
import sn.ecole.inscription.domain.CodeActivationRepository;

/** Implémentation Spring Data du port {@link CodeActivationRepository}. */
interface CodeActivationJpaRepository
    extends Repository<CodeActivation, UUID>, CodeActivationRepository {

  /** Verrou exclusif : deux activations simultanées du même code ne passent pas toutes deux. */
  @Override
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select c from CodeActivation c where c.identifiant = :identifiant"
          + " and c.statut = sn.ecole.inscription.domain.CodeActivation.Statut.ACTIF")
  Optional<CodeActivation> verrouillerActif(@Param("identifiant") String identifiant);
}
