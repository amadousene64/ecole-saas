package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Classe;
import sn.ecole.etablissement.domain.ClasseRepository;

/** Implémentation Spring Data du port {@link ClasseRepository}. */
interface ClasseJpaRepository extends Repository<Classe, UUID>, ClasseRepository {}
