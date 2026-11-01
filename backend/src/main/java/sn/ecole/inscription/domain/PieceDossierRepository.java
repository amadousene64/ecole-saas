package sn.ecole.inscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface PieceDossierRepository {

  Optional<PieceDossier> findById(UUID id);

  List<PieceDossier> findByEleveIdOrderByCreeLeDesc(UUID eleveId);

  List<PieceDossier> findByEleveIdIn(java.util.Collection<UUID> eleveIds);

  boolean existsByEleveId(UUID eleveId);

  PieceDossier save(PieceDossier piece);
}
