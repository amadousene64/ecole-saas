package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface EvenementCalendrierRepository {
  List<EvenementCalendrier> findByAnneeIdOrderByDateDebut(UUID anneeId);

  Optional<EvenementCalendrier> findById(UUID id);

  EvenementCalendrier save(EvenementCalendrier evenement);
}
