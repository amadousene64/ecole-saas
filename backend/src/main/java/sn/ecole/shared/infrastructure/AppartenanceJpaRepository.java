package sn.ecole.shared.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.shared.domain.Appartenance;
import sn.ecole.shared.domain.AppartenanceRepository;

/** Implémentation Spring Data du port {@link AppartenanceRepository}. */
interface AppartenanceJpaRepository
    extends Repository<Appartenance, UUID>, AppartenanceRepository {}
