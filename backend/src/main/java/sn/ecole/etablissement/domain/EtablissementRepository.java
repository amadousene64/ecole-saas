package sn.ecole.etablissement.domain;

import java.util.Optional;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface EtablissementRepository {
  Optional<Etablissement> findFirstBy();

  boolean existsBy();

  Etablissement save(Etablissement etablissement);
}
