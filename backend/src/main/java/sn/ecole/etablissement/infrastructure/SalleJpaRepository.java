package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Salle;
import sn.ecole.etablissement.domain.SalleRepository;

/** Implémentation Spring Data du port {@link SalleRepository}. */
interface SalleJpaRepository extends Repository<Salle, UUID>, SalleRepository {}
