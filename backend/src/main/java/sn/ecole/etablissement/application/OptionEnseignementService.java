package sn.ecole.etablissement.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.ClasseOptionRepository;
import sn.ecole.etablissement.domain.OptionEnseignement;
import sn.ecole.etablissement.domain.OptionEnseignementRepository;

/** Catalogue des options d'enseignement de l'école (LV2, arts…). */
@Service
@Transactional
public class OptionEnseignementService {

  public record Saisie(String code, String libelle, String groupe, boolean actif) {}

  private final OptionEnseignementRepository options;
  private final ClasseOptionRepository classesOptions;

  public OptionEnseignementService(
      OptionEnseignementRepository options, ClasseOptionRepository classesOptions) {
    this.options = options;
    this.classesOptions = classesOptions;
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<OptionEnseignement> lister() {
    return options.findAllByOrderByGroupeAscCodeAsc();
  }

  @PreAuthorize(Droits.ECRITURE)
  public OptionEnseignement creer(Saisie saisie) {
    OptionEnseignement option =
        OptionEnseignement.creer(saisie.code(), saisie.libelle(), saisie.groupe());
    verifierCodeLibre(option.getCode(), null);
    return options.save(option);
  }

  @PreAuthorize(Droits.ECRITURE)
  public OptionEnseignement modifier(UUID id, int version, Saisie saisie) {
    OptionEnseignement option = charger(id);
    Erreurs.verifierVersion(version, option);
    if (!option.getGroupe().equals(OptionEnseignement.groupe(saisie.groupe()))
        && classesOptions.existsByOptionId(id)) {
      // Le groupe fixe l'exclusivité des choix déjà faits par les élèves.
      throw Erreurs.conflit(
          "OPTION_UTILISEE", "Le groupe d'une option proposée par une classe ne change plus.");
    }
    // Avant de modifier l'entité : la requête de contrôle écrirait sinon la modification en base.
    verifierCodeLibre(
        saisie.code() == null ? "" : saisie.code().strip().toUpperCase(Locale.ROOT), id);
    option.modifier(saisie.code(), saisie.libelle(), saisie.groupe(), saisie.actif());
    return options.save(option);
  }

  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID id) {
    OptionEnseignement option = charger(id);
    if (classesOptions.existsByOptionId(id)) {
      throw Erreurs.conflit(
          "OPTION_UTILISEE", "Cette option est proposée par une classe : désactivez-la plutôt.");
    }
    option.supprimer(Instant.now());
    options.save(option);
  }

  OptionEnseignement charger(UUID id) {
    return options
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("OPTION_INTROUVABLE", "Option introuvable."));
  }

  private void verifierCodeLibre(String code, UUID sauf) {
    options
        .findByCode(code)
        .filter(autre -> !autre.getId().equals(sauf))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "OPTION_EXISTANTE", "Une option porte déjà le code " + code + ".");
            });
  }
}
