package sn.ecole.personnel.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface AffectationRepository {

  Optional<Affectation> findById(UUID id);

  List<Affectation> findByAnneeIdOrderByDebutAsc(UUID anneeId);

  List<Affectation> findByUtilisateurId(UUID utilisateurId);

  List<Affectation> findByClasseId(UUID classeId);

  List<Affectation> findByAnneeIdAndType(UUID anneeId, TypeAffectation type);

  Affectation save(Affectation affectation);
}
