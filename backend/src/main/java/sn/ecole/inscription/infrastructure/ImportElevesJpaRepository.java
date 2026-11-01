package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.inscription.domain.ImportEleves;
import sn.ecole.inscription.domain.ImportElevesRepository;

/** Implémentation Spring Data du port {@link ImportElevesRepository}. */
interface ImportElevesJpaRepository
    extends Repository<ImportEleves, UUID>, ImportElevesRepository {}
