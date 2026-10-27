package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.inscription.domain.ParametresInscription;
import sn.ecole.inscription.domain.ParametresInscriptionRepository;

/** Implémentation Spring Data du port {@link ParametresInscriptionRepository}. */
interface ParametresInscriptionJpaRepository
    extends Repository<ParametresInscription, UUID>, ParametresInscriptionRepository {}
