package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface ParametresCycleAnneeRepository {

  List<ParametresCycleAnnee> findByAnneeId(UUID anneeId);

  boolean existsByAnneeId(UUID anneeId);

  ParametresCycleAnnee save(ParametresCycleAnnee parametres);
}
