package sn.ecole.inscription.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface InscriptionRepository {

  Optional<Inscription> findById(UUID id);

  List<Inscription> findByEleveIdOrderByDateEntreeDesc(UUID eleveId);

  List<Inscription> findByEleveIdInAndStatutIn(
      Collection<UUID> eleveIds, Collection<StatutInscription> statuts);

  Optional<Inscription> findFirstByEleveIdAndAnneeIdAndStatutIn(
      UUID eleveId, UUID anneeId, Collection<StatutInscription> statuts);

  List<Inscription> findByClasseIdAndStatutIn(UUID classeId, Collection<StatutInscription> statuts);

  long countByClasseIdAndStatutIn(UUID classeId, Collection<StatutInscription> statuts);

  boolean existsByClasseId(UUID classeId);

  boolean existsByEleveId(UUID eleveId);

  Inscription save(Inscription inscription);

  /** Enregistre et écrit tout de suite (la contrainte d'unicité joue avant la suite). */
  Inscription saveAndFlush(Inscription inscription);
}
