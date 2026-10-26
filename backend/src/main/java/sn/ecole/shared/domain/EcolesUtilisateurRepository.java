package sn.ecole.shared.domain;

import java.util.List;
import java.util.UUID;

/**
 * Port de lecture des écoles d'UN utilisateur, hors école courante. L'implémentation passe par une
 * fonction SQL dédiée : l'application ne peut lire les appartenances d'aucune autre manière sans
 * école courante.
 */
public interface EcolesUtilisateurRepository {

  /** Écoles actives où l'utilisateur a un rôle non retiré, triées par nom. */
  List<EcoleUtilisateur> ecolesDe(UUID utilisateurId);
}
