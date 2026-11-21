package sn.ecole.inscription.domain;

import java.time.Instant;
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

  /**
   * Une page des brouillons en cours de l'école, les plus récemment modifiés d'abord (puis par id)
   * ; après la clé ({@code apresModifieLe}, {@code apresId}) quand elle est donnée.
   */
  List<BrouillonInscription> page(Instant apresModifieLe, UUID apresId, int limite);

  /** Brouillons en cours de cet auteur (plafond {@link BrouillonInscription#MAX_PAR_AUTEUR}). */
  long countByAuteurId(UUID auteurId);

  /** Brouillons en cours non modifiés depuis {@code avant} : à supprimer. */
  List<BrouillonInscription> findByModifieLeBefore(Instant avant);

  /** Enregistre et écrit tout de suite : la version renvoyée est celle de la base. */
  BrouillonInscription saveAndFlush(BrouillonInscription brouillon);
}
