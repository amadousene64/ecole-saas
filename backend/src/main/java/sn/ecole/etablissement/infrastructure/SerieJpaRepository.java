package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Serie;
import sn.ecole.etablissement.domain.SerieRepository;

/** Implémentation Spring Data du port {@link SerieRepository}. */
interface SerieJpaRepository extends Repository<Serie, UUID>, SerieRepository {}
