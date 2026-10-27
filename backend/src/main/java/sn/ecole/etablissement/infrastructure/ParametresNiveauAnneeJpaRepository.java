package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.ParametresNiveauAnnee;
import sn.ecole.etablissement.domain.ParametresNiveauAnneeRepository;

/** Implémentation Spring Data du port {@link ParametresNiveauAnneeRepository}. */
interface ParametresNiveauAnneeJpaRepository
    extends Repository<ParametresNiveauAnnee, UUID>, ParametresNiveauAnneeRepository {}
