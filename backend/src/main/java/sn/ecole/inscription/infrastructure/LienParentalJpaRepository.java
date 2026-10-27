package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.inscription.domain.LienParentalRepository;
import sn.ecole.shared.domain.ContexteTenant;

/** Implémentation Spring Data du port {@link LienParentalRepository}. */
interface LienParentalJpaRepository extends Repository<LienParental, UUID>, LienParentalRepository {

  /** SQL natif : les liens supprimés comptent aussi. Filtre tenant explicite (et RLS). */
  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM lien_parental WHERE tenant_id = :tenant"
              + " AND eleve_id = :eleve AND responsable_id = :responsable"
              + " AND NOT acces_espace_parent)",
      nativeQuery = true)
  boolean accesDejaRetire(
      @Param("tenant") UUID tenant,
      @Param("eleve") UUID eleveId,
      @Param("responsable") UUID responsableId);

  @Override
  default boolean accesDejaRetire(UUID eleveId, UUID responsableId) {
    return accesDejaRetire(ContexteTenant.exiger(), eleveId, responsableId);
  }
}
