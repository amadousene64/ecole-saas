package sn.ecole.etablissement.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.Creneau;
import sn.ecole.etablissement.domain.CreneauRepository;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.TypeCreneau;

/**
 * Grille horaire de chaque cycle : créneaux de cours et de pause, sans chevauchement un même jour.
 * Une école dont tous les cycles ont les mêmes horaires saisit une grille puis la copie.
 */
@Service
@Transactional
public class CreneauService {

  /** Saisie d'un créneau. */
  public record Saisie(
      LocalTime debut,
      LocalTime fin,
      Collection<DayOfWeek> jours,
      TypeCreneau type,
      String libelle) {}

  private final CreneauRepository creneaux;
  private final CycleRepository cycles;

  public CreneauService(CreneauRepository creneaux, CycleRepository cycles) {
    this.creneaux = creneaux;
    this.cycles = cycles;
  }

  /** Créneaux du cycle, par heure de début. */
  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<Creneau> lister(UUID cycleId) {
    exigerCycle(cycleId);
    return creneaux.findByCycleIdOrderByDebutAsc(cycleId);
  }

  @PreAuthorize(Droits.PEDAGOGIE)
  public Creneau creer(UUID cycleId, Saisie s) {
    exigerCycle(cycleId);
    Creneau creneau = Creneau.creer(cycleId, s.debut(), s.fin(), s.jours(), s.type(), s.libelle());
    verifierSansChevauchement(creneau);
    return creneaux.save(creneau);
  }

  @PreAuthorize(Droits.PEDAGOGIE)
  public Creneau modifier(UUID id, int version, Saisie s) {
    Creneau creneau = charger(id);
    Erreurs.verifierVersion(version, creneau);
    creneau.modifier(s.debut(), s.fin(), s.jours(), s.type(), s.libelle());
    verifierSansChevauchement(creneau);
    return creneaux.save(creneau);
  }

  @PreAuthorize(Droits.PEDAGOGIE)
  public void supprimer(UUID id) {
    Creneau creneau = charger(id);
    creneau.supprimer(Instant.now());
    creneaux.save(creneau);
  }

  /** Copie la grille d'un cycle dans un autre, qui doit être encore vide. */
  @PreAuthorize(Droits.PEDAGOGIE)
  public List<Creneau> copier(UUID depuisCycleId, UUID versCycleId) {
    exigerCycle(depuisCycleId);
    exigerCycle(versCycleId);
    if (depuisCycleId.equals(versCycleId)) {
      throw Erreurs.invalide("COPIE_INVALIDE", "Choisissez un autre cycle que celui à remplir.");
    }
    if (!creneaux.findByCycleIdOrderByDebutAsc(versCycleId).isEmpty()) {
      throw Erreurs.conflit(
          "GRILLE_NON_VIDE",
          "Ce cycle a déjà des créneaux : supprimez-les avant de copier une autre grille.");
    }
    return creneaux.findByCycleIdOrderByDebutAsc(depuisCycleId).stream()
        .map(c -> creneaux.save(c.copierVers(versCycleId)))
        .toList();
  }

  private void verifierSansChevauchement(Creneau creneau) {
    creneaux.findByCycleIdOrderByDebutAsc(creneau.getCycleId()).stream()
        .filter(autre -> !autre.getId().equals(creneau.getId()))
        .filter(creneau::chevauche)
        .findFirst()
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "CRENEAU_CHEVAUCHEMENT",
                  "Ce créneau chevauche celui de "
                      + autre.getDebut()
                      + " à "
                      + autre.getFin()
                      + " un même jour.");
            });
  }

  private void exigerCycle(UUID cycleId) {
    cycles
        .findById(cycleId)
        .orElseThrow(() -> Erreurs.introuvable("CYCLE_INTROUVABLE", "Cycle introuvable."));
  }

  private Creneau charger(UUID id) {
    return creneaux
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("CRENEAU_INTROUVABLE", "Créneau introuvable."));
  }
}
