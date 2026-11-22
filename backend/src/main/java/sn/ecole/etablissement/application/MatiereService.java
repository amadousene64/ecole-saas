package sn.ecole.etablissement.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.etablissement.domain.Matiere;
import sn.ecole.etablissement.domain.MatiereRepository;
import sn.ecole.etablissement.domain.ModeleSenegalais;

/**
 * Matières de l'école, dans l'ordre d'affichage. Une matière qui ne sert plus se désactive ; la
 * suppression (logique) sera refusée quand des affectations ou un emploi du temps l'utiliseront.
 */
@Service
@Transactional
public class MatiereService {

  /** Saisie d'une matière. */
  public record Saisie(
      String code, String libelle, String groupe, Collection<CodeCycle> cycles, int ordre) {}

  private final MatiereRepository matieres;

  public MatiereService(MatiereRepository matieres) {
    this.matieres = matieres;
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<Matiere> lister() {
    return matieres.findAllByOrderByOrdreAscLibelleAsc();
  }

  @PreAuthorize(Droits.PEDAGOGIE)
  public Matiere creer(Saisie s) {
    Matiere matiere = Matiere.creer(s.code(), s.libelle(), s.groupe(), s.cycles(), s.ordre());
    verifierCodeLibre(matiere.getCode(), null);
    return matieres.save(matiere);
  }

  @PreAuthorize(Droits.PEDAGOGIE)
  public Matiere modifier(UUID id, int version, Saisie s, boolean actif) {
    Matiere matiere = charger(id);
    Erreurs.verifierVersion(version, matiere);
    // Avant de modifier l'entité : la requête de contrôle écrirait sinon la modification en base.
    verifierCodeLibre(s.code() == null ? "" : s.code().strip().toUpperCase(Locale.ROOT), id);
    matiere.modifier(s.code(), s.libelle(), s.groupe(), s.cycles(), s.ordre(), actif);
    return matieres.save(matiere);
  }

  @PreAuthorize(Droits.PEDAGOGIE)
  public void supprimer(UUID id) {
    Matiere matiere = charger(id);
    matiere.supprimer(Instant.now());
    matieres.save(matiere);
  }

  /**
   * Ajoute les matières usuelles du modèle sénégalais dont le code manque encore ; n'en modifie
   * aucune. Renvoie celles ajoutées (aucune au second appel).
   */
  @PreAuthorize(Droits.PEDAGOGIE)
  public List<Matiere> ajouterModele() {
    List<Matiere> ajoutees = new ArrayList<>();
    for (ModeleSenegalais.ModeleMatiere m : ModeleSenegalais.MATIERES) {
      if (matieres.findByCode(m.code()).isEmpty()) {
        ajoutees.add(
            matieres.save(Matiere.creer(m.code(), m.libelle(), m.groupe(), m.cycles(), m.ordre())));
      }
    }
    return ajoutees;
  }

  private Matiere charger(UUID id) {
    return matieres
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("MATIERE_INTROUVABLE", "Matière introuvable."));
  }

  private void verifierCodeLibre(String code, UUID sauf) {
    matieres
        .findByCode(code)
        .filter(autre -> !autre.getId().equals(sauf))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "MATIERE_EXISTANTE", "Une matière porte déjà le code " + code + ".");
            });
  }
}
