package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Matiere;
import sn.ecole.etablissement.domain.MatiereRepository;

/** Implémentation Spring Data du port {@link MatiereRepository}. */
interface MatiereJpaRepository extends Repository<Matiere, UUID>, MatiereRepository {}
