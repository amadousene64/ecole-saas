package sn.ecole.etablissement.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;
import sn.ecole.etablissement.domain.ClasseRepository;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.Niveau;
import sn.ecole.etablissement.domain.NiveauRepository;
import sn.ecole.etablissement.domain.NiveauSerie;
import sn.ecole.etablissement.domain.NiveauSerieRepository;
import sn.ecole.etablissement.domain.SerieRepository;
import sn.ecole.etablissement.domain.StatutAnnee;
import sn.ecole.shared.application.JournalAudit;

/** Niveaux d'études, leur base de moyenne éventuelle et les séries qu'ils proposent. */
@Service
@Transactional
public class NiveauService {

  /** Un niveau, la base de moyenne qui s'y applique réellement et ses séries. */
  public record VueNiveau(Niveau niveau, int baseMoyenneEffective, List<UUID> serieIds) {}

  private final NiveauRepository niveaux;
  private final CycleRepository cycles;
  private final SerieRepository series;
  private final NiveauSerieRepository liens;
  private final ClasseRepository classes;
  private final AnneeScolaireRepository annees;
  private final JournalAudit journal;

  public NiveauService(
      NiveauRepository niveaux,
      CycleRepository cycles,
      SerieRepository series,
      NiveauSerieRepository liens,
      ClasseRepository classes,
      AnneeScolaireRepository annees,
      JournalAudit journal) {
    this.niveaux = niveaux;
    this.cycles = cycles;
    this.series = series;
    this.liens = liens;
    this.classes = classes;
    this.annees = annees;
    this.journal = journal;
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<VueNiveau> lister() {
    Map<UUID, Cycle> parCycle =
        cycles.findAll().stream().collect(Collectors.toMap(Cycle::getId, c -> c));
    Map<UUID, List<UUID>> seriesParNiveau =
        liens.findAll().stream()
            .collect(
                Collectors.groupingBy(
                    NiveauSerie::getNiveauId,
                    Collectors.mapping(NiveauSerie::getSerieId, Collectors.toList())));
    return niveaux.findAllByOrderByOrdre().stream()
        .map(
            n ->
                new VueNiveau(
                    n,
                    n.baseMoyenneEffective(parCycle.get(n.getCycleId())),
                    seriesParNiveau.getOrDefault(n.getId(), List.of())))
        .toList();
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public VueNiveau lire(UUID id) {
    return vue(charger(id));
  }

  /** Une base de moyenne propre au niveau est un paramètre de notation : directeur seulement. */
  @PreAuthorize(Droits.ECRITURE)
  public VueNiveau creer(UUID cycleId, String code, String libelle, int ordre, Integer base) {
    cycles
        .findById(cycleId)
        .orElseThrow(() -> Erreurs.introuvable("CYCLE_INTROUVABLE", "Cycle introuvable."));
    if (base != null) {
      Erreurs.exigerDirecteur();
      verifierBaseModifiable();
    }
    Niveau niveau = Niveau.creer(cycleId, code, libelle, ordre, base);
    verifierCodeLibre(niveau.getCode(), null);
    niveaux.save(niveau);
    return vue(niveau);
  }

  @PreAuthorize(Droits.ECRITURE)
  public VueNiveau modifier(
      UUID id, int version, String libelle, int ordre, Integer base, boolean actif) {
    Niveau niveau = charger(id);
    Erreurs.verifierVersion(version, niveau);
    if (!niveau.aLaBase(base)) {
      Erreurs.exigerDirecteur();
      verifierBaseModifiable();
      journal.consigner(
          "NIVEAU_BASE_MODIFIEE",
          "Niveau",
          id,
          Map.of("avant", String.valueOf(niveau.getBaseMoyenne()), "apres", String.valueOf(base)));
    }
    niveau.modifier(libelle, ordre, base, actif);
    niveaux.save(niveau);
    return vue(niveau);
  }

  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID id) {
    Niveau niveau = charger(id);
    if (niveau.getBaseMoyenne() != null) {
      // Sinon : supprimer puis recréer sans base contournerait la règle « base = directeur seul ».
      Erreurs.exigerDirecteur();
      verifierBaseModifiable();
    }
    if (classes.existsByNiveauId(id)) {
      throw Erreurs.conflit("NIVEAU_UTILISE", "Des classes utilisent ce niveau.");
    }
    Instant maintenant = Instant.now();
    for (NiveauSerie lien : liens.findByNiveauId(id)) {
      lien.retirer(maintenant);
      liens.save(lien);
    }
    niveau.supprimer(maintenant);
    niveaux.save(niveau);
  }

  /** Remplace l'ensemble des séries du niveau. Vide : le niveau n'a plus de série. */
  @PreAuthorize(Droits.ECRITURE)
  public VueNiveau definirSeries(UUID id, Set<UUID> serieIds) {
    Niveau niveau = charger(id);
    for (UUID serieId : serieIds) {
      series
          .findById(serieId)
          .orElseThrow(() -> Erreurs.introuvable("SERIE_INTROUVABLE", "Série introuvable."));
    }
    List<NiveauSerie> actuels = liens.findByNiveauId(id);
    Set<UUID> anciennes = actuels.stream().map(NiveauSerie::getSerieId).collect(Collectors.toSet());
    // Passer de « sans série » à « avec séries » (ou l'inverse) rendrait les classes incohérentes.
    if (anciennes.isEmpty() != serieIds.isEmpty() && classes.existsByNiveauId(id)) {
      throw Erreurs.conflit(
          "NIVEAU_UTILISE",
          "Des classes utilisent ce niveau : leurs séries seraient incohérentes.");
    }
    Instant maintenant = Instant.now();
    for (NiveauSerie lien : actuels) {
      if (!serieIds.contains(lien.getSerieId())) {
        if (classes.existsByNiveauIdAndSerieId(id, lien.getSerieId())) {
          throw Erreurs.conflit("SERIE_UTILISEE", "Une classe utilise cette série dans ce niveau.");
        }
        lien.retirer(maintenant);
        liens.save(lien);
      }
    }
    for (UUID serieId : new ArrayList<>(new HashSet<>(serieIds))) {
      if (!anciennes.contains(serieId)) {
        liens.save(NiveauSerie.associer(id, serieId));
      }
    }
    return vue(niveau);
  }

  private void verifierBaseModifiable() {
    if (annees.existsByStatutIn(List.of(StatutAnnee.EN_COURS, StatutAnnee.CLOTUREE))) {
      throw Erreurs.conflit(
          "CYCLE_VERROUILLE", "La base de la moyenne ne change plus une fois une année ouverte.");
    }
  }

  private Niveau charger(UUID id) {
    return niveaux
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("NIVEAU_INTROUVABLE", "Niveau introuvable."));
  }

  private void verifierCodeLibre(String code, UUID sauf) {
    niveaux
        .findByCode(code)
        .filter(autre -> !autre.getId().equals(sauf))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "NIVEAU_EXISTANT", "Un niveau porte déjà le code " + code + ".");
            });
  }

  private VueNiveau vue(Niveau niveau) {
    Cycle cycle =
        cycles
            .findById(niveau.getCycleId())
            .orElseThrow(() -> Erreurs.introuvable("CYCLE_INTROUVABLE", "Cycle introuvable."));
    List<UUID> serieIds =
        liens.findByNiveauId(niveau.getId()).stream().map(NiveauSerie::getSerieId).toList();
    return new VueNiveau(niveau, niveau.baseMoyenneEffective(cycle), serieIds);
  }
}
