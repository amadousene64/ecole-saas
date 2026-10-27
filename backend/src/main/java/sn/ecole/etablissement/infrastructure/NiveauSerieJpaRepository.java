package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.NiveauSerie;
import sn.ecole.etablissement.domain.NiveauSerieRepository;

/** Implémentation Spring Data du port {@link NiveauSerieRepository}. */
interface NiveauSerieJpaRepository extends Repository<NiveauSerie, UUID>, NiveauSerieRepository {}
