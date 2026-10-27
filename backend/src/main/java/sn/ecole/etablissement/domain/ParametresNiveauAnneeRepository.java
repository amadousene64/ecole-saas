package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface ParametresNiveauAnneeRepository {

  List<ParametresNiveauAnnee> findByAnneeId(UUID anneeId);

  ParametresNiveauAnnee save(ParametresNiveauAnnee parametres);
}
