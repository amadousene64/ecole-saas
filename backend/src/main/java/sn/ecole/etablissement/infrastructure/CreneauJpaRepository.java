package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Creneau;
import sn.ecole.etablissement.domain.CreneauRepository;

/** Implémentation Spring Data du port {@link CreneauRepository}. */
interface CreneauJpaRepository extends Repository<Creneau, UUID>, CreneauRepository {}
