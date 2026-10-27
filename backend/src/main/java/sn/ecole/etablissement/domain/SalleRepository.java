package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface SalleRepository {
  List<Salle> findAllByOrderByNom();

  Optional<Salle> findById(UUID id);

  Optional<Salle> findByNomIgnoreCase(String nom);

  Salle save(Salle salle);
}
