package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Periode;
import sn.ecole.etablissement.domain.PeriodeRepository;

/** Implémentation Spring Data du port {@link PeriodeRepository}. */
interface PeriodeJpaRepository extends Repository<Periode, UUID>, PeriodeRepository {}
