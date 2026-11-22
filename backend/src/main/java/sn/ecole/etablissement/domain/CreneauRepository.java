package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface CreneauRepository {

  List<Creneau> findByCycleIdOrderByDebutAsc(UUID cycleId);

  Optional<Creneau> findById(UUID id);

  Creneau save(Creneau creneau);
}
