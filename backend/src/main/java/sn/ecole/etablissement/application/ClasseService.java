package sn.ecole.etablissement.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.Classe;
import sn.ecole.etablissement.domain.ClasseRepository;
import sn.ecole.etablissement.domain.Niveau;
import sn.ecole.etablissement.domain.NiveauRepository;
import sn.ecole.etablissement.domain.NiveauSerie;
import sn.ecole.etablissement.domain.NiveauSerieRepository;
import sn.ecole.etablissement.domain.ReglesClasse;
import sn.ecole.etablissement.domain.SalleRepository;
import sn.ecole.etablissement.domain.StatutAnnee;

/**
 * Classes d'une année : nom unique par année, série obligatoire seulement si le niveau en a. Rien
 * ne se crée ni ne change dans une année clôturée.
 */
@Service
@Transactional
public class ClasseService {

  public record Saisie(UUID niveauId, UUID serieId, String nom, int capacite, UUID salleId) {}

  private final ClasseRepository classes;
  private final AnneeScolaireRepository annees;
  private final NiveauRepository niveaux;
  private final NiveauSerieRepository liens;
  private final SalleRepository salles;

  public ClasseService(
      ClasseRepository classes,
      AnneeScolaireRepository annees,
      NiveauRepository niveaux,
      NiveauSerieRepository liens,
      SalleRepository salles) {
    this.classes = classes;
    this.annees = annees;
    this.niveaux = niveaux;
    this.liens = liens;
    this.salles = salles;
  }

  /**
   * @param anneeId l'année voulue, ou {@code null} pour l'année en cours (liste vide s'il n'y en a
   *     pas)
   * @param niveauId filtre facultatif
   */
  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<Classe> lister(UUID anneeId, UUID niveauId) {
    UUID annee = anneeId;
    if (annee == null) {
      annee = annees.findByStatut(StatutAnnee.EN_COURS).map(AnneeScolaire::getId).orElse(null);
      if (annee == null) {
        return List.of();
      }
    } else {
      chargerAnnee(annee);
    }
    return niveauId == null
        ? classes.findByAnneeIdOrderByNom(annee)
        : classes.findByAnneeIdAndNiveauIdOrderByNom(annee, niveauId);
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public Classe lire(UUID id) {
    return charger(id);
  }

  @PreAuthorize(Droits.ECRITURE)
  public Classe creer(UUID anneeId, Saisie saisie) {
    chargerAnnee(anneeId).verifierModifiable();
    valider(saisie);
    verifierNomLibre(anneeId, saisie.nom(), null);
    return classes.save(
        Classe.creer(
            anneeId,
            saisie.niveauId(),
            saisie.serieId(),
            saisie.nom(),
            saisie.capacite(),
            saisie.salleId()));
  }

  @PreAuthorize(Droits.ECRITURE)
  public Classe modifier(UUID id, int version, Saisie saisie) {
    Classe classe = charger(id);
    AnneeScolaire annee = chargerAnnee(classe.getAnneeId());
    annee.verifierModifiable();
    Erreurs.verifierVersion(version, classe);
    if (annee.getStatut() == StatutAnnee.EN_COURS
        && !saisie.niveauId().equals(classe.getNiveauId())) {
      // Le niveau fixe la base de la moyenne et les coefficients : il ne change plus en cours
      // d'année.
      throw Erreurs.conflit(
          "NIVEAU_CLASSE_FIGE", "Le niveau d'une classe ne change plus une fois l'année en cours.");
    }
    if (annee.getStatut() == StatutAnnee.EN_COURS
        && !Objects.equals(saisie.serieId(), classe.getSerieId())) {
      // La série fixe les coefficients : changer en cours d'année mélangerait deux barèmes dans les
      // moyennes et le bilan annuel.
      throw Erreurs.conflit(
          "SERIE_CLASSE_FIGEE", "La série d'une classe ne change plus une fois l'année en cours.");
    }
    valider(saisie);
    // Avant de modifier l'entité : la requête de contrôle écrirait sinon la modification en base.
    verifierNomLibre(classe.getAnneeId(), saisie.nom(), id);
    classe.modifier(
        saisie.niveauId(), saisie.serieId(), saisie.nom(), saisie.capacite(), saisie.salleId());
    return classes.save(classe);
  }

  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID id) {
    Classe classe = charger(id);
    chargerAnnee(classe.getAnneeId()).verifierModifiable();
    // À bloquer quand des élèves y seront inscrits (module inscription).
    classe.supprimer(Instant.now());
    classes.save(classe);
  }

  private void valider(Saisie saisie) {
    Niveau niveau =
        niveaux
            .findById(saisie.niveauId())
            .orElseThrow(() -> Erreurs.introuvable("NIVEAU_INTROUVABLE", "Niveau introuvable."));
    if (!niveau.isActif()) {
      throw Erreurs.regle("NIVEAU_INACTIF", "Ce niveau n'est plus utilisé.");
    }
    Set<UUID> series =
        liens.findByNiveauId(niveau.getId()).stream()
            .map(NiveauSerie::getSerieId)
            .collect(Collectors.toSet());
    ReglesClasse.verifierSerie(series, saisie.serieId());
    if (saisie.salleId() != null) {
      salles
          .findById(saisie.salleId())
          .orElseThrow(() -> Erreurs.introuvable("SALLE_INTROUVABLE", "Salle introuvable."));
    }
  }

  private void verifierNomLibre(UUID anneeId, String nom, UUID sauf) {
    classes
        .findByAnneeIdAndNomIgnoreCase(anneeId, nom == null ? "" : nom.strip())
        .filter(autre -> !autre.getId().equals(sauf))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "CLASSE_EXISTANTE", "Une classe " + nom + " existe déjà cette année.");
            });
  }

  private Classe charger(UUID id) {
    return classes
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("CLASSE_INTROUVABLE", "Classe introuvable."));
  }

  private AnneeScolaire chargerAnnee(UUID id) {
    return annees
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("ANNEE_INTROUVABLE", "Année scolaire introuvable."));
  }
}
