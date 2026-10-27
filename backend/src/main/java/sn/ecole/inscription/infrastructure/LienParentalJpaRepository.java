package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.inscription.domain.LienParentalRepository;

/** Implémentation Spring Data du port {@link LienParentalRepository}. */
interface LienParentalJpaRepository
    extends Repository<LienParental, UUID>, LienParentalRepository {}
