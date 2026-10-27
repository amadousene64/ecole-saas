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

  /**
   * Vrai si un lien entre cet élève et ce responsable, même supprimé, a eu son accès à l'espace
   * parent retiré : la restriction survit à la suppression du lien.
   */
  boolean accesDejaRetire(UUID eleveId, UUID responsableId);

  LienParental save(LienParental lien);
}
