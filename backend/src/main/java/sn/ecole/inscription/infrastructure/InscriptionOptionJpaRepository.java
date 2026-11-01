package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import sn.ecole.inscription.domain.InscriptionOption;
import sn.ecole.inscription.domain.InscriptionOptionRepository;

/** Implémentation Spring Data du port {@link InscriptionOptionRepository}. */
interface InscriptionOptionJpaRepository
    extends Repository<InscriptionOption, UUID>, InscriptionOptionRepository {

  @Override
  @Query(
      "select count(io) > 0 from InscriptionOption io, Inscription i"
          + " where io.inscriptionId = i.id and i.classeId = :classe and io.optionId = :option")
  boolean optionChoisieDansClasse(@Param("classe") UUID classeId, @Param("option") UUID optionId);
}
