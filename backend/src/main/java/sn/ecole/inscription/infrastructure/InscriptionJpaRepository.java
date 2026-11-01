package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.inscription.domain.Inscription;
import sn.ecole.inscription.domain.InscriptionRepository;

/** Implémentation Spring Data du port {@link InscriptionRepository}. */
interface InscriptionJpaRepository extends Repository<Inscription, UUID>, InscriptionRepository {}
