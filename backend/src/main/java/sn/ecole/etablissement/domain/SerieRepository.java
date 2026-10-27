package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface SerieRepository {
  List<Serie> findAllByOrderByCode();

  Optional<Serie> findById(UUID id);

  Optional<Serie> findByCode(String code);

  Serie save(Serie serie);
}
