package sn.ecole.inscription.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface LienParentalRepository {

  Optional<LienParental> findById(UUID id);

  List<LienParental> findByEleveId(UUID eleveId);

  List<LienParental> findByResponsableId(UUID responsableId);

  List<LienParental> findByResponsableIdIn(Collection<UUID> responsableIds);

  Optional<LienParental> findByEleveIdAndResponsableId(UUID eleveId, UUID responsableId);

  boolean existsByEleveId(UUID eleveId);

  LienParental save(LienParental lien);
}
