package sn.ecole.inscription.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.EleveRepository;

/**
 * Compte élève : réservé aux élèves du collège et du lycée (cycles MOYEN et SECONDAIRE), d'après
 * leur inscription active de l'année en cours. Appelé par {@link ActivationService}, qui contrôle
 * les droits.
 */
@Component
class ActivationEleve {

  private final EleveRepository eleves;

  ActivationEleve(EleveRepository eleves) {
    this.eleves = eleves;
  }

  /**
   * @throws sn.ecole.shared.domain.ErreurMetier ACTIVATION_ELEVE_INTERDITE sans inscription active
   *     au collège ou au lycée
   */
  void verifierEligible(UUID eleveId) {
    throw Erreurs.regle(
        "ACTIVATION_ELEVE_INTERDITE",
        "Un compte élève n'est ouvert qu'aux élèves inscrits au collège ou au lycée.");
  }

  void relier(UUID eleveId, UUID utilisateur) {
    Eleve eleve = eleves.findById(eleveId).orElseThrow(Erreurs::eleveIntrouvable);
    eleves
        .findByUtilisateurId(utilisateur)
        .filter(autre -> !autre.getId().equals(eleveId))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "COMPTE_DEJA_UTILISE", "Votre compte est déjà relié à un autre élève.");
            });
    eleve.relierCompte(utilisateur);
    eleves.save(eleve);
  }
}
