package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.EleveRepository;
import sn.ecole.shared.domain.ContexteTenant;

/** Implémentation Spring Data du port {@link EleveRepository}. */
interface EleveJpaRepository extends Repository<Eleve, UUID>, EleveRepository {

  /** SQL natif : les élèves supprimés comptent aussi. Filtre tenant explicite (et RLS). */
  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM eleve WHERE tenant_id = :tenant AND matricule = :matricule)",
      nativeQuery = true)
  boolean matriculeExiste(@Param("tenant") UUID tenant, @Param("matricule") String matricule);

  @Override
  default boolean matriculeExiste(String matricule) {
    return matriculeExiste(ContexteTenant.exiger(), matricule);
  }
}
