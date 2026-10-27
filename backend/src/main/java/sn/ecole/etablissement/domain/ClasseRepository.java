package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface ClasseRepository {
  List<Classe> findByAnneeIdOrderByNom(UUID anneeId);

  List<Classe> findByAnneeIdAndNiveauIdOrderByNom(UUID anneeId, UUID niveauId);

  Optional<Classe> findById(UUID id);

  Optional<Classe> findByAnneeIdAndNomIgnoreCase(UUID anneeId, String nom);

  boolean existsByNiveauId(UUID niveauId);

  boolean existsByNiveauIdAndSerieId(UUID niveauId, UUID serieId);

  boolean existsBySerieId(UUID serieId);

  boolean existsBySalleId(UUID salleId);

  Classe save(Classe classe);
}
