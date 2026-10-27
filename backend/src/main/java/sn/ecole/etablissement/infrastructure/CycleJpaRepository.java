package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;

/** Implémentation Spring Data du port {@link CycleRepository}. */
interface CycleJpaRepository extends Repository<Cycle, UUID>, CycleRepository {}
