package sn.ecole.inscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface BrouillonInscriptionRepository {

  Optional<BrouillonInscription> findById(UUID id);

  /**
   * Brouillon en cours, verrouillé jusqu'à la fin de la transaction : un dépôt de pièce et une
   * finalisation (ou un abandon) simultanés se suivent, aucune pièce ne reste sur un brouillon
   * clos.
   */
  Optional<BrouillonInscription> verrouiller(UUID id);

  /** Brouillons en cours de l'école, les plus récemment modifiés d'abord. */
  List<BrouillonInscription> findTop50ByOrderByModifieLeDesc();

  /** Enregistre et écrit tout de suite : la version renvoyée est celle de la base. */
  BrouillonInscription saveAndFlush(BrouillonInscription brouillon);
}
