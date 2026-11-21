package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.inscription.domain.BrouillonInscription;
import sn.ecole.inscription.domain.BrouillonInscriptionRepository;

/** Implémentation Spring Data du port {@link BrouillonInscriptionRepository}. */
interface BrouillonInscriptionJpaRepository
    extends Repository<BrouillonInscription, UUID>, BrouillonInscriptionRepository {}
