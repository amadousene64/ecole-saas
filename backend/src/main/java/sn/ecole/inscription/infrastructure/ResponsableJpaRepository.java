package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.inscription.domain.Responsable;
import sn.ecole.inscription.domain.ResponsableRepository;

/** Implémentation Spring Data du port {@link ResponsableRepository}. */
interface ResponsableJpaRepository extends Repository<Responsable, UUID>, ResponsableRepository {}
