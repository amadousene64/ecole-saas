package sn.ecole.inscription.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.inscription.domain.Inscription;
import sn.ecole.inscription.domain.InscriptionRepository;
import sn.ecole.inscription.domain.StatutInscription;
import sn.ecole.shared.application.PerimetreScolaire;

/**
 * Périmètre appliqué aux élèves : un enseignant ou un surveillant ne voit que les élèves inscrits
 * (pré-inscrits ou inscrits) dans une classe de son périmètre ({@link PerimetreScolaire}). Le
 * personnel qui voit toute l'école n'est pas filtré ; un parent ou un élève garde son accès propre
 * ({@link PerimetreEleve}).
 */
@Component
class PerimetreEleves {

  private final PerimetreScolaire perimetre;
  private final PerimetreEleve perimetreFamille;
  private final InscriptionRepository inscriptions;
  private final ReferentielScolaire referentiel;

  PerimetreEleves(
      PerimetreScolaire perimetre,
      PerimetreEleve perimetreFamille,
      InscriptionRepository inscriptions,
      ReferentielScolaire referentiel) {
    this.perimetre = perimetre;
    this.perimetreFamille = perimetreFamille;
    this.inscriptions = inscriptions;
    this.referentiel = referentiel;
  }

  boolean toutLEcole() {
    return perimetre.toutLEcole();
  }

  /** Refus (403) si l'élève n'est dans aucune classe du périmètre (ni enfant ou soi-même). */
  void exigerEleve(UUID eleveId) {
    if (perimetre.toutLEcole()) {
      return;
    }
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && perimetreFamille.autorise(auth, eleveId, "lire")) {
      return;
    }
    List<UUID> classes =
        inscriptions.findByEleveIdInAndStatutIn(List.of(eleveId), StatutInscription.ACTIFS).stream()
            .map(Inscription::getClasseId)
            .toList();
    if (perimetre.filtrer(classes).isEmpty()) {
      throw new AccessDeniedException("Élève hors de votre périmètre.");
    }
  }

  void exigerClasse(UUID classeId) {
    perimetre.exigerClasse(classeId);
  }

  Set<UUID> filtrer(List<UUID> classeIds) {
    return perimetre.filtrer(classeIds);
  }

  /**
   * Classes de l'année en cours que voit l'utilisateur, pour filtrer une recherche ; vide sans
   * année en cours.
   */
  Set<UUID> classesVisiblesCetteAnnee() {
    return referentiel
        .anneeEnCours()
        .map(
            a ->
                perimetre.filtrer(
                    referentiel.classes(a.id()).stream()
                        .map(ReferentielScolaire.ClasseScolaire::id)
                        .toList()))
        .orElse(Set.of());
  }
}
