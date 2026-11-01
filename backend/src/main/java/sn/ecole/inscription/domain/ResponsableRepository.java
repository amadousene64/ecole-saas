package sn.ecole.inscription.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface ResponsableRepository {

  Optional<Responsable> findById(UUID id);

  List<Responsable> findByIdIn(Collection<UUID> ids);

  List<Responsable> findByTelephoneOrderByNomRecherche(String telephone);

  List<Responsable> findTop50ByNomRechercheContainingOrderByNomRecherche(String terme);

  Optional<Responsable> findByUtilisateurId(UUID utilisateurId);

  Responsable save(Responsable responsable);
}
