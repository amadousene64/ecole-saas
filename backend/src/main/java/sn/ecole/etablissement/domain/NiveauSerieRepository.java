package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface NiveauSerieRepository {
  List<NiveauSerie> findAll();

  List<NiveauSerie> findByNiveauId(UUID niveauId);

  boolean existsBySerieId(UUID serieId);

  NiveauSerie save(NiveauSerie lien);
}
