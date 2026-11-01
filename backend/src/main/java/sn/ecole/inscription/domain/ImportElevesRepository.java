package sn.ecole.inscription.domain;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface ImportElevesRepository {

  boolean existsByEmpreinte(String empreinte);

  ImportEleves save(ImportEleves trace);
}
