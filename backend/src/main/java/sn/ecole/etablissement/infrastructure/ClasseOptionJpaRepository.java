package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.ClasseOption;
import sn.ecole.etablissement.domain.ClasseOptionRepository;

/** Implémentation Spring Data du port {@link ClasseOptionRepository}. */
interface ClasseOptionJpaRepository
    extends Repository<ClasseOption, UUID>, ClasseOptionRepository {}
