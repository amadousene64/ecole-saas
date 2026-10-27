package sn.ecole.etablissement.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.ClasseRepository;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.Niveau;
import sn.ecole.etablissement.domain.NiveauRepository;
import sn.ecole.etablissement.domain.ParametresCycleAnneeRepository;
import sn.ecole.etablissement.domain.ParametresNiveauAnneeRepository;
import sn.ecole.etablissement.domain.Periode;
import sn.ecole.etablissement.domain.PeriodeRepository;
import sn.ecole.etablissement.domain.StatutAnnee;

/**
 * Lecture de la structure scolaire pour les autres modules (année en cours, périodes, notation).
 */
@Service
@Transactional(readOnly = true)
public class ReferentielScolaireService implements ReferentielScolaire {

  private final AnneeScolaireRepository annees;
  private final PeriodeRepository periodes;
  private final CycleRepository cycles;
  private final NiveauRepository niveaux;
  private final ClasseRepository classes;
  private final ParametresCycleAnneeRepository parametresCycles;
  private final ParametresNiveauAnneeRepository parametresNiveaux;

  public ReferentielScolaireService(
      AnneeScolaireRepository annees,
      PeriodeRepository periodes,
      CycleRepository cycles,
      NiveauRepository niveaux,
      ClasseRepository classes,
      ParametresCycleAnneeRepository parametresCycles,
      ParametresNiveauAnneeRepository parametresNiveaux) {
    this.annees = annees;
    this.periodes = periodes;
    this.cycles = cycles;
    this.niveaux = niveaux;
    this.classes = classes;
    this.parametresCycles = parametresCycles;
    this.parametresNiveaux = parametresNiveaux;
  }

  @Override
  @PreAuthorize(Droits.LECTURE_TOUS)
  public Optional<AnneeEnCours> anneeEnCours() {
    return annees
        .findByStatut(StatutAnnee.EN_COURS)
        .map(a -> new AnneeEnCours(a.getId(), a.getLibelle(), a.getDateDebut(), a.getDateFin()));
  }

  @Override
  @PreAuthorize(Droits.LECTURE_TOUS)
  public List<PeriodeScolaire> periodes(UUID anneeId) {
    return periodes.findByAnneeId(anneeId).stream()
        .sorted(Comparator.comparing(Periode::getCycleId).thenComparingInt(Periode::getNumero))
        .map(
            p ->
                new PeriodeScolaire(
                    p.getId(),
                    p.getCycleId(),
                    p.getNumero(),
                    p.getLibelle(),
                    p.getDateDebut(),
                    p.getDateFin()))
        .toList();
  }

  /** Réservé au personnel : une classe ne concerne ni les parents ni les élèves. */
  @Override
  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  public ClasseScolaire classe(UUID classeId) {
    var classe =
        classes
            .findById(classeId)
            .orElseThrow(() -> Erreurs.introuvable("CLASSE_INTROUVABLE", "Classe introuvable."));
    UUID cycleId =
        niveaux
            .findById(classe.getNiveauId())
            .orElseThrow(() -> Erreurs.introuvable("NIVEAU_INTROUVABLE", "Niveau introuvable."))
            .getCycleId();
    return new ClasseScolaire(
        classe.getId(),
        classe.getAnneeId(),
        classe.getNiveauId(),
        classe.getSerieId(),
        cycleId,
        classe.getNom());
  }

  @Override
  @PreAuthorize(Droits.LECTURE_TOUS)
  public ParametresNotation parametresNotation(UUID anneeId) {
    AnneeScolaire annee =
        annees
            .findById(anneeId)
            .orElseThrow(
                () -> Erreurs.introuvable("ANNEE_INTROUVABLE", "Année scolaire introuvable."));
    Map<UUID, Cycle> parCycle = new HashMap<>();
    cycles.findAll().forEach(c -> parCycle.put(c.getId(), c));
    List<Niveau> modele = niveaux.findAllByOrderByOrdre();
    Map<UUID, Integer> ordre = new HashMap<>();
    modele.forEach(n -> ordre.put(n.getId(), n.getOrdre()));

    if (annee.getStatut() == StatutAnnee.PREPARATION) {
      return new ParametresNotation(
          anneeId,
          false,
          parCycle.values().stream()
              .sorted(Comparator.comparing(Cycle::getCode))
              .map(
                  c ->
                      new ParametresCycle(
                          c.getId(),
                          c.getCode(),
                          c.getTypePeriode(),
                          c.getModeNotation(),
                          c.getBaseMoyenne()))
              .toList(),
          modele.stream()
              .map(
                  n ->
                      new ParametresNiveau(
                          n.getId(),
                          n.getCycleId(),
                          n.baseMoyenneEffective(parCycle.get(n.getCycleId()))))
              .toList());
    }
    return new ParametresNotation(
        anneeId,
        true,
        parametresCycles.findByAnneeId(anneeId).stream()
            .sorted(Comparator.comparing(p -> parCycle.get(p.getCycleId()).getCode()))
            .map(
                p ->
                    new ParametresCycle(
                        p.getCycleId(),
                        parCycle.get(p.getCycleId()).getCode(),
                        p.getTypePeriode(),
                        p.getModeNotation(),
                        p.getBaseMoyenne()))
            .toList(),
        parametresNiveaux.findByAnneeId(anneeId).stream()
            .sorted(Comparator.comparingInt(p -> ordre.getOrDefault(p.getNiveauId(), 999)))
            .map(p -> new ParametresNiveau(p.getNiveauId(), p.getCycleId(), p.getBaseMoyenne()))
            .toList());
  }
}
