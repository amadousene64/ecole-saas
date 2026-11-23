package sn.ecole.personnel.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface MembrePersonnelRepository {

  Optional<MembrePersonnel> findByUtilisateurId(UUID utilisateurId);

  List<MembrePersonnel> findByUtilisateurIdIn(Collection<UUID> utilisateurIds);

  MembrePersonnel save(MembrePersonnel membre);
}
