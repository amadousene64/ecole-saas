package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface MatiereRepository {

  List<Matiere> findAllByOrderByOrdreAscLibelleAsc();

  Optional<Matiere> findById(UUID id);

  Optional<Matiere> findByCode(String code);

  Matiere save(Matiere matiere);
}
