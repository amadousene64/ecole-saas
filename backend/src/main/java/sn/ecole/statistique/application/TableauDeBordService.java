package sn.ecole.statistique.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
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
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.ReferentielScolaire.AnneeEnCours;
import sn.ecole.etablissement.ReferentielScolaire.ClasseScolaire;
import sn.ecole.etablissement.ReferentielScolaire.NiveauScolaire;
import sn.ecole.inscription.StatistiquesInscription;
import sn.ecole.inscription.StatistiquesInscription.EffectifClasse;
import sn.ecole.inscription.StatistiquesInscription.InscriptionsSemaine;
import sn.ecole.inscription.StatistiquesInscription.SuiviClasses;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.statistique.domain.CacheTableauDeBord;
import sn.ecole.statistique.domain.PeriodeCourbe;
import sn.ecole.statistique.domain.TableauDeBord;
import sn.ecole.statistique.domain.TableauDeBord.EffectifCycle;
import sn.ecole.statistique.domain.TableauDeBord.EffectifNiveau;
import sn.ecole.statistique.domain.TableauDeBord.EffectifStatut;
import sn.ecole.statistique.domain.TableauDeBord.Part;
import sn.ecole.statistique.domain.TableauDeBord.Semaine;

/**
 * Tableau de bord de la direction, pour l'année en cours. Réservé au directeur : le contrôle est
 * fait avant tout accès au cache, qui ne remplace jamais le droit. Le cache est par école (la clé
 * porte l'identifiant de l'école courante).
 */
@Service
public class TableauDeBordService {

  /** Ordre du cycle de vie d'une inscription. */
  private static final List<String> STATUTS =
      List.of("PRE_INSCRIT", "INSCRIT", "TRANSFERE", "RADIE", "ABANDON");

  private static final Map<CodeCycle, String> LIBELLES_CYCLES =
      Map.of(
          CodeCycle.ELEMENTAIRE, "Primaire",
          CodeCycle.MOYEN, "Collège",
          CodeCycle.SECONDAIRE, "Lycée");

  private final ReferentielScolaire referentiel;
  private final StatistiquesInscription inscriptions;
  private final CacheTableauDeBord cache;
  private final Clock horloge;

  public TableauDeBordService(
      ReferentielScolaire referentiel,
      StatistiquesInscription inscriptions,
      CacheTableauDeBord cache,
      Clock horloge) {
    this.referentiel = referentiel;
    this.inscriptions = inscriptions;
    this.cache = cache;
    this.horloge = horloge;
  }

  /**
   * @param cycle null : tous les cycles
   */
  @PreAuthorize(Droits.DIRECTION)
  @Transactional(readOnly = true)
  public TableauDeBord direction(PeriodeCourbe periode, CodeCycle cycle) {
    UUID tenantId = ContexteTenant.exiger();
    Optional<AnneeEnCours> annee = referentiel.anneeEnCours();
    if (annee.isEmpty()) {
      return vide(periode, cycle);
    }
    AnneeEnCours courante = annee.get();
    Optional<TableauDeBord> garde = cache.lire(tenantId, courante.id(), periode, cycle);
    if (garde.isPresent()) {
      return garde.get();
    }
    TableauDeBord calcule = calculer(courante, periode, cycle);
    cache.ecrire(tenantId, courante.id(), periode, cycle, calcule);
    return calcule;
  }

  private TableauDeBord vide(PeriodeCourbe periode, CodeCycle cycle) {
    return new TableauDeBord(
        null,
        null,
        horloge.instant(),
        periode,
        cycle,
        0,
        List.of(),
        List.of(),
        statuts(Map.of()),
        Part.de(0, 0),
        Part.de(0, 0),
        List.of());
  }

  private TableauDeBord calculer(AnneeEnCours annee, PeriodeCourbe periode, CodeCycle cycle) {
    List<ClasseScolaire> classes =
        referentiel.classes(annee.id()).stream()
            .filter(c -> cycle == null || c.codeCycle() == cycle)
            .toList();
    Map<UUID, ClasseScolaire> parClasse =
        classes.stream().collect(Collectors.toMap(ClasseScolaire::id, c -> c));
    Set<UUID> classeIds = parClasse.keySet();

    // Une ligne par classe et par statut ; on ne garde que les classes du filtre.
    List<EffectifClasse> effectifs =
        inscriptions.effectifs(annee.id()).stream()
            .filter(e -> parClasse.containsKey(e.classeId()))
            .toList();

    Map<String, Long> parStatut = new HashMap<>();
    Map<UUID, Long> inscritsParNiveau = new HashMap<>();
    Map<CodeCycle, Long> inscritsParCycle = new EnumMap<>(CodeCycle.class);
    long inscrits = 0;
    for (EffectifClasse e : effectifs) {
      parStatut.merge(e.statut(), e.nombre(), Long::sum);
      if ("INSCRIT".equals(e.statut())) {
        ClasseScolaire classe = parClasse.get(e.classeId());
        inscritsParNiveau.merge(classe.niveauId(), e.nombre(), Long::sum);
        inscritsParCycle.merge(classe.codeCycle(), e.nombre(), Long::sum);
        inscrits += e.nombre();
      }
    }

    List<EffectifNiveau> parNiveau = new ArrayList<>();
    for (NiveauScolaire n : referentiel.niveaux()) {
      if (cycle == null || n.codeCycle() == cycle) {
        parNiveau.add(
            new EffectifNiveau(
                n.id(),
                n.code(),
                n.libelle(),
                n.codeCycle(),
                inscritsParNiveau.getOrDefault(n.id(), 0L)));
      }
    }
    List<EffectifCycle> parCycle = new ArrayList<>();
    for (CodeCycle code : CodeCycle.values()) {
      if (cycle == null || cycle == code) {
        parCycle.add(
            new EffectifCycle(
                code, LIBELLES_CYCLES.get(code), inscritsParCycle.getOrDefault(code, 0L)));
      }
    }

    SuiviClasses suivi = inscriptions.suivi(annee.id(), classeIds);
    LocalDate aujourdhui = LocalDate.now(horloge);
    LocalDate debut = debutCourbe(periode, annee, aujourdhui);
    LocalDate fin = finCourbe(periode, annee, aujourdhui, debut);
    List<Semaine> courbe =
        inscriptions.inscriptionsParSemaine(annee.id(), classeIds, debut, fin).stream()
            .map((InscriptionsSemaine s) -> new Semaine(s.lundi(), s.nombre()))
            .toList();

    return new TableauDeBord(
        annee.id(),
        annee.libelle(),
        horloge.instant(),
        periode,
        cycle,
        inscrits,
        parCycle,
        parNiveau,
        statuts(parStatut),
        Part.de(suivi.eleves(), suivi.dossiersComplets()),
        Part.de(suivi.familles(), suivi.famillesConnectees()),
        courbe);
  }

  /** Tous les statuts, y compris à zéro. */
  private static List<EffectifStatut> statuts(Map<String, Long> comptes) {
    return STATUTS.stream().map(s -> new EffectifStatut(s, comptes.getOrDefault(s, 0L))).toList();
  }

  static LocalDate debutCourbe(PeriodeCourbe periode, AnneeEnCours annee, LocalDate aujourdhui) {
    return switch (periode) {
      case JOURS_30 -> aujourdhui.minusDays(29);
      case JOURS_90 -> aujourdhui.minusDays(89);
      case ANNEE -> annee.dateDebut().minusMonths(3);
    };
  }

  /** Jamais dans le futur, jamais avant le début (plage vide si l'année n'a pas commencé). */
  static LocalDate finCourbe(
      PeriodeCourbe periode, AnneeEnCours annee, LocalDate aujourdhui, LocalDate debut) {
    LocalDate fin = aujourdhui;
    if (periode == PeriodeCourbe.ANNEE && annee.dateFin().isBefore(fin)) {
      fin = annee.dateFin();
    }
    return fin.isBefore(debut) ? debut : fin;
  }
}
