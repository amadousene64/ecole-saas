package sn.ecole.inscription.domain;

import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface DossierSensibleRepository {

  Optional<DossierSensible> findByEleveId(UUID eleveId);

  /** Enregistre et écrit tout de suite : la version renvoyée est celle de la base. */
  DossierSensible saveAndFlush(DossierSensible dossier);
}
