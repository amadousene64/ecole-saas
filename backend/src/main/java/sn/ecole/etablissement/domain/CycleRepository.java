package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface CycleRepository {
  List<Cycle> findAll();

  Optional<Cycle> findById(UUID id);

  /**
   * Cycle verrouillé jusqu'à la fin de la transaction : les écritures dans sa grille horaire
   * (chevauchement, copie dans une grille vide) se suivent au lieu de se croiser.
   */
  Optional<Cycle> verrouiller(UUID id);

  Cycle save(Cycle cycle);
}
