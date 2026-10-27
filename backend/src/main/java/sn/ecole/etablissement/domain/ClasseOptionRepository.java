package sn.ecole.etablissement.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface ClasseOptionRepository {
  List<ClasseOption> findByClasseId(UUID classeId);

  List<ClasseOption> findByClasseIdIn(Collection<UUID> classeIds);

  boolean existsByOptionId(UUID optionId);

  ClasseOption save(ClasseOption lien);
}
