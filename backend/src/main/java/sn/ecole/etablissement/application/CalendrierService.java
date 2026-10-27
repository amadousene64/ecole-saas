package sn.ecole.etablissement.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.EvenementCalendrier;
import sn.ecole.etablissement.domain.EvenementCalendrierRepository;
import sn.ecole.etablissement.domain.ReglesCalendrier;
import sn.ecole.etablissement.domain.TypeEvenement;

/** Jours fériés et vacances d'une année. Lecture seule une fois l'année clôturée. */
@Service
@Transactional
public class CalendrierService {

  private final AnneeScolaireRepository annees;
  private final EvenementCalendrierRepository evenements;

  public CalendrierService(
      AnneeScolaireRepository annees, EvenementCalendrierRepository evenements) {
    this.annees = annees;
    this.evenements = evenements;
  }

  @PreAuthorize(Droits.LECTURE_TOUS)
  @Transactional(readOnly = true)
  public List<EvenementCalendrier> lister(UUID anneeId) {
    charger(anneeId);
    return evenements.findByAnneeIdOrderByDateDebut(anneeId);
  }

  @PreAuthorize(Droits.ECRITURE)
  public EvenementCalendrier ajouter(
      UUID anneeId, TypeEvenement type, String libelle, LocalDate debut, LocalDate fin) {
    AnneeScolaire annee = charger(anneeId);
    annee.verifierModifiable();
    EvenementCalendrier evenement = EvenementCalendrier.creer(anneeId, type, libelle, debut, fin);
    ReglesCalendrier.verifier(
        annee, type, debut, fin, evenements.findByAnneeIdOrderByDateDebut(anneeId));
    return evenements.save(evenement);
  }

  @PreAuthorize(Droits.ECRITURE)
  public EvenementCalendrier modifier(
      UUID anneeId,
      UUID evenementId,
      int version,
      TypeEvenement type,
      String libelle,
      LocalDate debut,
      LocalDate fin) {
    AnneeScolaire annee = charger(anneeId);
    annee.verifierModifiable();
    EvenementCalendrier evenement = chargerEvenement(anneeId, evenementId);
    Erreurs.verifierVersion(version, evenement);
    List<EvenementCalendrier> autres =
        evenements.findByAnneeIdOrderByDateDebut(anneeId).stream()
            .filter(e -> !e.getId().equals(evenementId))
            .toList();
    evenement.modifier(type, libelle, debut, fin);
    ReglesCalendrier.verifier(annee, type, debut, fin, autres);
    return evenements.save(evenement);
  }

  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID anneeId, UUID evenementId) {
    charger(anneeId).verifierModifiable();
    EvenementCalendrier evenement = chargerEvenement(anneeId, evenementId);
    evenement.supprimer(Instant.now());
    evenements.save(evenement);
  }

  private EvenementCalendrier chargerEvenement(UUID anneeId, UUID evenementId) {
    return evenements
        .findById(evenementId)
        .filter(e -> e.getAnneeId().equals(anneeId))
        .orElseThrow(() -> Erreurs.introuvable("EVENEMENT_INTROUVABLE", "Événement introuvable."));
  }

  private AnneeScolaire charger(UUID id) {
    return annees
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("ANNEE_INTROUVABLE", "Année scolaire introuvable."));
  }
}
