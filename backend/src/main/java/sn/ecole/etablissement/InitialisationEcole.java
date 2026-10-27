package sn.ecole.etablissement;

import java.util.UUID;

/**
 * Initialise la structure d'une nouvelle école : profil, cycles, niveaux et séries du modèle
 * sénégalais. Appel système (futur super-administrateur, profil dev) : il n'est exposé par aucun
 * endpoint de l'école, qui ne peut donc jamais se créer elle-même.
 */
public interface InitialisationEcole {

  /**
   * Crée la structure de l'école {@code tenantId} dans sa propre transaction.
   *
   * @throws sn.ecole.shared.domain.ErreurMetier si l'école est déjà initialisée
   */
  void initialiser(UUID tenantId, String nom, String code, StatutEtablissement statut);

  /** Vrai si l'école a déjà son profil. */
  boolean estInitialisee(UUID tenantId);
}
