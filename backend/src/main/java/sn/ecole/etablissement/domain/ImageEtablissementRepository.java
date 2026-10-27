package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface ImageEtablissementRepository {
  Optional<ImageEtablissement> findByType(TypeImage type);

  List<ImageEtablissement> findAll();

  ImageEtablissement save(ImageEtablissement image);
}
