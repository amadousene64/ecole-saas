package sn.ecole.etablissement.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.Classe;
import sn.ecole.etablissement.domain.ClasseOption;
import sn.ecole.etablissement.domain.ClasseOptionRepository;
import sn.ecole.etablissement.domain.ClasseRepository;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.Niveau;
import sn.ecole.etablissement.domain.NiveauRepository;
import sn.ecole.etablissement.domain.OptionEnseignement;
import sn.ecole.etablissement.domain.OptionEnseignementRepository;
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
  private final ClasseOptionRepository classesOptions;
  private final OptionEnseignementRepository options;

  public ReferentielScolaireService(
      AnneeScolaireRepository annees,
      PeriodeRepository periodes,
      CycleRepository cycles,
      NiveauRepository niveaux,
      ClasseRepository classes,
      ParametresCycleAnneeRepository parametresCycles,
      ParametresNiveauAnneeRepository parametresNiveaux,
      ClasseOptionRepository classesOptions,
      OptionEnseignementRepository options) {
    this.annees = annees;
    this.periodes = periodes;
    this.cycles = cycles;
    this.niveaux = niveaux;
    this.classes = classes;
    this.parametresCycles = parametresCycles;
    this.parametresNiveaux = parametresNiveaux;
    this.classesOptions = classesOptions;
    this.options = options;
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

  @Override
  @PreAuthorize(Droits.LECTURE_TOUS)
  public AnneeScolaireInfo annee(UUID anneeId) {
    AnneeScolaire a =
        annees
            .findById(anneeId)
            .orElseThrow(
                () -> Erreurs.introuvable("ANNEE_INTROUVABLE", "Année scolaire introuvable."));
    return new AnneeScolaireInfo(
        a.getId(),
        a.getLibelle(),
        a.getDateDebut(),
        a.getDateFin(),
        a.getStatut() == StatutAnnee.EN_COURS,
        a.getStatut() == StatutAnnee.CLOTUREE);
  }

  /** Réservé au personnel : une classe ne concerne ni les parents ni les élèves. */
  @Override
  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  public ClasseScolaire classe(UUID classeId) {
    var classe =
        classes
            .findById(classeId)
            .orElseThrow(() -> Erreurs.introuvable("CLASSE_INTROUVABLE", "Classe introuvable."));
    return versClasseScolaire(List.of(classe)).getFirst();
  }

  @Override
  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  public List<ClasseScolaire> classes(UUID anneeId) {
    return versClasseScolaire(classes.findByAnneeIdOrderByNom(anneeId));
  }

  @Override
  @PreAuthorize(Droits.LECTURE_TOUS)
  public Map<UUID, String> nomsClasses(Collection<UUID> classeIds) {
    if (classeIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> noms = new HashMap<>();
    classes.findByIdIn(Set.copyOf(classeIds)).forEach(c -> noms.put(c.getId(), c.getNom()));
    return noms;
  }

  @Override
  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  public List<NiveauScolaire> niveaux() {
    Map<UUID, Cycle> parCycle = new HashMap<>();
    cycles.findAll().forEach(c -> parCycle.put(c.getId(), c));
    return niveaux.findAllByOrderByOrdre().stream()
        .map(
            n ->
                new NiveauScolaire(
                    n.getId(),
                    n.getCode(),
                    n.getLibelle(),
                    n.getOrdre(),
                    parCycle.get(n.getCycleId()).getCode()))
        .toList();
  }

  private List<ClasseScolaire> versClasseScolaire(List<Classe> liste) {
    if (liste.isEmpty()) {
      return List.of();
    }
    Map<UUID, Niveau> parNiveau = new HashMap<>();
    niveaux.findAllByOrderByOrdre().forEach(n -> parNiveau.put(n.getId(), n));
    Map<UUID, Cycle> parCycle = new HashMap<>();
    cycles.findAll().forEach(c -> parCycle.put(c.getId(), c));
    List<ClasseOption> liens =
        classesOptions.findByClasseIdIn(liste.stream().map(Classe::getId).toList());
    Map<UUID, OptionEnseignement> parOption = new HashMap<>();
    options
        .findByIdIn(liens.stream().map(ClasseOption::getOptionId).collect(Collectors.toSet()))
        .forEach(o -> parOption.put(o.getId(), o));
    Map<UUID, List<OptionScolaire>> optionsParClasse = new HashMap<>();
    for (ClasseOption lien : liens) {
      OptionEnseignement o = parOption.get(lien.getOptionId());
      if (o != null) {
        optionsParClasse
            .computeIfAbsent(lien.getClasseId(), k -> new ArrayList<>())
            .add(new OptionScolaire(o.getId(), o.getCode(), o.getLibelle(), o.getGroupe()));
      }
    }
    return liste.stream()
        .map(
            classe -> {
              Niveau niveau = parNiveau.get(classe.getNiveauId());
              if (niveau == null) {
                throw Erreurs.introuvable("NIVEAU_INTROUVABLE", "Niveau introuvable.");
              }
              Cycle cycle = parCycle.get(niveau.getCycleId());
              List<OptionScolaire> proposees =
                  optionsParClasse.getOrDefault(classe.getId(), List.of()).stream()
                      .sorted(
                          Comparator.comparing(OptionScolaire::groupe)
                              .thenComparing(OptionScolaire::code))
                      .toList();
              return new ClasseScolaire(
                  classe.getId(),
                  classe.getAnneeId(),
                  classe.getNiveauId(),
                  classe.getSerieId(),
                  niveau.getCycleId(),
                  cycle.getCode(),
                  classe.getNom(),
                  classe.getCapacite(),
                  proposees);
            })
        .toList();
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
