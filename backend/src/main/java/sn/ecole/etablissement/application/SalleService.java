package sn.ecole.etablissement.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.ClasseRepository;
import sn.ecole.etablissement.domain.Salle;
import sn.ecole.etablissement.domain.SalleRepository;

@Service
@Transactional
public class SalleService {

  private final SalleRepository salles;
  private final ClasseRepository classes;

  public SalleService(SalleRepository salles, ClasseRepository classes) {
    this.salles = salles;
    this.classes = classes;
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<Salle> lister() {
    return salles.findAllByOrderByNom();
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public Salle lire(UUID id) {
    return charger(id);
  }

  @PreAuthorize(Droits.ECRITURE)
  public Salle creer(String nom, int capacite) {
    Salle salle = Salle.creer(nom, capacite);
    verifierNomLibre(salle.getNom(), null);
    return salles.save(salle);
  }

  @PreAuthorize(Droits.ECRITURE)
  public Salle modifier(UUID id, int version, String nom, int capacite, boolean actif) {
    Salle salle = charger(id);
    Erreurs.verifierVersion(version, salle);
    salle.modifier(nom, capacite, actif);
    verifierNomLibre(salle.getNom(), id);
    return salles.save(salle);
  }

  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID id) {
    Salle salle = charger(id);
    if (classes.existsBySalleId(id)) {
      throw Erreurs.conflit("SALLE_UTILISEE", "Des classes utilisent cette salle.");
    }
    salle.supprimer(Instant.now());
    salles.save(salle);
  }

  private Salle charger(UUID id) {
    return salles
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("SALLE_INTROUVABLE", "Salle introuvable."));
  }

  private void verifierNomLibre(String nom, UUID sauf) {
    salles
        .findByNomIgnoreCase(nom)
        .filter(autre -> !autre.getId().equals(sauf))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit("SALLE_EXISTANTE", "Une salle porte déjà le nom " + nom + ".");
            });
  }
}
