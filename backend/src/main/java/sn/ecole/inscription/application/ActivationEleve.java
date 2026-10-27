package sn.ecole.inscription.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.EleveRepository;

/**
 * Compte élève : réservé aux élèves du collège et du lycée (cycles MOYEN et SECONDAIRE), d'après
 * leurs inscriptions actives. Appelé par {@link ActivationService}, qui contrôle les droits.
 */
@Component
class ActivationEleve {

  static final Set<CodeCycle> CYCLES_AVEC_COMPTE = Set.of(CodeCycle.MOYEN, CodeCycle.SECONDAIRE);

  private final EleveRepository eleves;
  private final InscriptionService inscriptions;
  private final ReferentielScolaire referentiel;

  ActivationEleve(
      EleveRepository eleves, InscriptionService inscriptions, ReferentielScolaire referentiel) {
    this.eleves = eleves;
    this.inscriptions = inscriptions;
    this.referentiel = referentiel;
  }

  /**
   * @throws sn.ecole.shared.domain.ErreurMetier ACTIVATION_ELEVE_INTERDITE sans inscription active
   *     au collège ou au lycée
   */
  void verifierEligible(UUID eleveId) {
    boolean eligible =
        inscriptions.actives(eleveId).stream()
            .anyMatch(
                i -> CYCLES_AVEC_COMPTE.contains(referentiel.classe(i.getClasseId()).codeCycle()));
    if (!eligible) {
      throw Erreurs.regle(
          "ACTIVATION_ELEVE_INTERDITE",
          "Un compte élève n'est ouvert qu'aux élèves inscrits au collège ou au lycée.");
    }
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
