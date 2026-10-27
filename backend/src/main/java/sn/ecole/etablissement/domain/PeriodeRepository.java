package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface PeriodeRepository {
  List<Periode> findByAnneeId(UUID anneeId);

  List<Periode> findByAnneeIdAndCycleId(UUID anneeId, UUID cycleId);

  Optional<Periode> findById(UUID id);

  Periode save(Periode periode);
}
