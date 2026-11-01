package sn.ecole.inscription.domain;

import java.util.Optional;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface ParametresInscriptionRepository {

  Optional<ParametresInscription> findFirstBy();

  ParametresInscription save(ParametresInscription parametres);
}
