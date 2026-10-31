package sn.ecole.inscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface CodeActivationRepository {

  /** Code actif portant cet identifiant, verrouillé pour la durée de la transaction. */
  Optional<CodeActivation> verrouillerActif(String identifiant);

  boolean existsByIdentifiantAndStatut(String identifiant, CodeActivation.Statut statut);

  List<CodeActivation> findByCibleAndCibleIdAndStatut(
      CodeActivation.Cible cible, UUID cibleId, CodeActivation.Statut statut);

  CodeActivation save(CodeActivation code);
}
