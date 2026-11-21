package sn.ecole.inscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface BrouillonInscriptionRepository {

  Optional<BrouillonInscription> findById(UUID id);

  /** Brouillons en cours de l'école, les plus récemment modifiés d'abord. */
  List<BrouillonInscription> findTop50ByOrderByModifieLeDesc();

  /** Enregistre et écrit tout de suite : la version renvoyée est celle de la base. */
  BrouillonInscription saveAndFlush(BrouillonInscription brouillon);
}
