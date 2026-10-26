package sn.ecole.shared.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port des appartenances ; toujours limité à l'école courante (filtre Hibernate + RLS). Pas de
 * suppression physique : un retrait passe par {@link Appartenance#revoquer}.
 */
public interface AppartenanceRepository {

  List<Appartenance> findByUtilisateurId(UUID utilisateurId);

  Optional<Appartenance> findByUtilisateurIdAndRole(UUID utilisateurId, RoleEcole role);

  Appartenance save(Appartenance appartenance);
}
