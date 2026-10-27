package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.ParametresCycleAnnee;
import sn.ecole.etablissement.domain.ParametresCycleAnneeRepository;

/** Implémentation Spring Data du port {@link ParametresCycleAnneeRepository}. */
interface ParametresCycleAnneeJpaRepository
    extends Repository<ParametresCycleAnnee, UUID>, ParametresCycleAnneeRepository {}
