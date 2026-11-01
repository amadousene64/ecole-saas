package sn.ecole.etablissement.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface OptionEnseignementRepository {
  List<OptionEnseignement> findAllByOrderByGroupeAscCodeAsc();

  List<OptionEnseignement> findByIdIn(Collection<UUID> ids);

  Optional<OptionEnseignement> findById(UUID id);

  Optional<OptionEnseignement> findByCode(String code);

  OptionEnseignement save(OptionEnseignement option);
}
