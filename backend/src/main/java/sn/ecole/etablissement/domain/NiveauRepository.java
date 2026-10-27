package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface NiveauRepository {
  List<Niveau> findAllByOrderByOrdre();

  Optional<Niveau> findById(UUID id);

  Optional<Niveau> findByCode(String code);

  Niveau save(Niveau niveau);
}
