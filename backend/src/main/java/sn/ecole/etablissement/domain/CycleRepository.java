package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface CycleRepository {
  List<Cycle> findAll();

  Optional<Cycle> findById(UUID id);

  Cycle save(Cycle cycle);
}
