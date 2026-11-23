package sn.ecole.personnel.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.personnel.domain.Affectation;
import sn.ecole.personnel.domain.AffectationRepository;

/** Implémentation Spring Data du port {@link AffectationRepository}. */
interface AffectationJpaRepository extends Repository<Affectation, UUID>, AffectationRepository {}
