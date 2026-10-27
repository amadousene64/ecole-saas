package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.EvenementCalendrier;
import sn.ecole.etablissement.domain.EvenementCalendrierRepository;

/** Implémentation Spring Data du port {@link EvenementCalendrierRepository}. */
interface EvenementCalendrierJpaRepository
    extends Repository<EvenementCalendrier, UUID>, EvenementCalendrierRepository {}
