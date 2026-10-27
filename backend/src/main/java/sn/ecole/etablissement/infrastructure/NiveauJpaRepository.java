package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Niveau;
import sn.ecole.etablissement.domain.NiveauRepository;

/** Implémentation Spring Data du port {@link NiveauRepository}. */
interface NiveauJpaRepository extends Repository<Niveau, UUID>, NiveauRepository {}
