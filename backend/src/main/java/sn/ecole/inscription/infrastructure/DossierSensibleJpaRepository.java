package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.inscription.domain.DossierSensible;
import sn.ecole.inscription.domain.DossierSensibleRepository;

/** Implémentation Spring Data du port {@link DossierSensibleRepository}. */
interface DossierSensibleJpaRepository
    extends Repository<DossierSensible, UUID>, DossierSensibleRepository {}
