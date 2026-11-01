package sn.ecole.statistique.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import sn.ecole.statistique.domain.TableauDeBord.Part;

/** Agrégation, filtres et cache du tableau de bord, sans base (les ports sont simulés). */
class TableauDeBordServiceTest {

  private static final UUID ECOLE = UUID.fromString("01930000-0000-7000-8000-0000000000a1");
  private static final UUID ANNEE = UUID.fromString("01930000-0000-7000-8000-00000000aa01");

  private static final UUID CP = UUID.fromString("01930000-0000-7000-8000-00000000c001");
  private static final UUID SIXIEME = UUID.fromString("01930000-0000-7000-8000-00000000c002");
  private static final UUID TERMINALE = UUID.fromString("01930000-0000-7000-8000-00000000c003");

  private static final UUID CLASSE_CP = UUID.fromString("01930000-0000-7000-8000-00000000d001");
  private static final UUID CLASSE_6E = UUID.fromString("01930000-0000-7000-8000-00000000d002");
  private static final UUID CLASSE_TLE = UUID.fromString("01930000-0000-7000-8000-00000000d003");

  private final ReferentielScolaire referentiel = mock(ReferentielScolaire.class);
  private final StatistiquesInscription inscriptions = mock(StatistiquesInscription.class);
  private final MemoireCache cache = new MemoireCache();
  private final Clock horloge =
      Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneId.of("Africa/Dakar"));
  private final TableauDeBordService service =
      new TableauDeBordService(referentiel, inscriptions, cache, horloge);

  /** Cache en mémoire qui compte ses écritures. */
  static class MemoireCache implements CacheTableauDeBord {
    final Map<String, TableauDeBord> valeurs = new HashMap<>();
    int ecritures;

    private static String cle(UUID t, UUID a, PeriodeCourbe p, CodeCycle c) {
      return t + "|" + a + "|" + p + "|" + c;
    }

    @Override
    public Optional<TableauDeBord> lire(UUID t, UUID a, PeriodeCourbe p, CodeCycle c) {
      return Optional.ofNullable(valeurs.get(cle(t, a, p, c)));
    }

    @Override
    public void ecrire(UUID t, UUID a, PeriodeCourbe p, CodeCycle c, TableauDeBord tableau) {
      ecritures++;
      valeurs.put(cle(t, a, p, c), tableau);
    }
  }

  @BeforeEach
  void referentiel() {
    when(referentiel.anneeEnCours())
        .thenReturn(
            Optional.of(
                new AnneeEnCours(
                    ANNEE, "2026-2027", LocalDate.of(2026, 10, 5), LocalDate.of(2027, 7, 2))));
    when(referentiel.niveaux())
        .thenReturn(
            List.of(
                new NiveauScolaire(CP, "CP", "Cours préparatoire", 1, CodeCycle.ELEMENTAIRE),
                new NiveauScolaire(SIXIEME, "6E", "Sixième", 7, CodeCycle.MOYEN),
                new NiveauScolaire(TERMINALE, "TLE", "Terminale", 13, CodeCycle.SECONDAIRE)));
    when(referentiel.classes(ANNEE))
        .thenReturn(
            List.of(
                classe(CLASSE_CP, CP, CodeCycle.ELEMENTAIRE),
                classe(CLASSE_6E, SIXIEME, CodeCycle.MOYEN),
                classe(CLASSE_TLE, TERMINALE, CodeCycle.SECONDAIRE)));
    when(inscriptions.effectifs(ANNEE))
        .thenReturn(
            List.of(
                new EffectifClasse(CLASSE_CP, "INSCRIT", 20),
                new EffectifClasse(CLASSE_CP, "PRE_INSCRIT", 3),
                new EffectifClasse(CLASSE_6E, "INSCRIT", 30),
                new EffectifClasse(CLASSE_6E, "ABANDON", 1),
                new EffectifClasse(CLASSE_TLE, "INSCRIT", 10),
                new EffectifClasse(CLASSE_TLE, "RADIE", 2)));
    when(inscriptions.suivi(eq(ANNEE), anyCollection()))
        .thenReturn(new SuiviClasses(63, 42, 50, 20));
    when(inscriptions.inscriptionsParSemaine(eq(ANNEE), anyCollection(), any(), any()))
        .thenReturn(List.of(new InscriptionsSemaine(LocalDate.of(2026, 11, 2), 7)));
  }

  private static ClasseScolaire classe(UUID id, UUID niveau, CodeCycle cycle) {
    return new ClasseScolaire(
        id, ANNEE, niveau, null, UUID.randomUUID(), cycle, "C", 40, List.of());
  }

  private TableauDeBord direction(PeriodeCourbe periode, CodeCycle cycle) throws Exception {
    return ContexteTenant.executer(ECOLE, () -> service.direction(periode, cycle));
  }

  @Test
  void agregeLesEffectifsParCycleParNiveauEtParStatut() throws Exception {
    TableauDeBord t = direction(PeriodeCourbe.ANNEE, null);

    assertThat(t.anneeLibelle()).isEqualTo("2026-2027");
    assertThat(t.inscrits()).isEqualTo(60);
    assertThat(t.parCycle())
        .extracting("cycle", "libelle", "nombre")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(CodeCycle.ELEMENTAIRE, "Primaire", 20L),
            org.assertj.core.groups.Tuple.tuple(CodeCycle.MOYEN, "Collège", 30L),
            org.assertj.core.groups.Tuple.tuple(CodeCycle.SECONDAIRE, "Lycée", 10L));
    assertThat(t.parNiveau())
        .extracting("code", "nombre")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("CP", 20L),
            org.assertj.core.groups.Tuple.tuple("6E", 30L),
            org.assertj.core.groups.Tuple.tuple("TLE", 10L));
    // Tous les statuts, dans l'ordre du cycle de vie, y compris ceux à zéro.
    assertThat(t.parStatut())
        .extracting("statut", "nombre")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("PRE_INSCRIT", 3L),
            org.assertj.core.groups.Tuple.tuple("INSCRIT", 60L),
            org.assertj.core.groups.Tuple.tuple("TRANSFERE", 0L),
            org.assertj.core.groups.Tuple.tuple("RADIE", 2L),
            org.assertj.core.groups.Tuple.tuple("ABANDON", 1L));
    assertThat(t.dossiers()).isEqualTo(new Part(63, 42, 67));
    assertThat(t.familles()).isEqualTo(new Part(50, 20, 40));
    assertThat(t.courbe()).singleElement().satisfies(s -> assertThat(s.nombre()).isEqualTo(7));
  }

  @Test
  void leFiltreDeCycleNeGardeQueSesClassesPartout() throws Exception {
    TableauDeBord t = direction(PeriodeCourbe.ANNEE, CodeCycle.MOYEN);

    assertThat(t.cycle()).isEqualTo(CodeCycle.MOYEN);
    assertThat(t.inscrits()).isEqualTo(30);
    assertThat(t.parCycle()).extracting("cycle").containsExactly(CodeCycle.MOYEN);
    assertThat(t.parNiveau()).extracting("code").containsExactly("6E");
    assertThat(t.parStatut())
        .filteredOn(s -> s.statut().equals("ABANDON"))
        .extracting("nombre")
        .containsExactly(1L);
    // Le suivi et la courbe ne portent que sur les classes du cycle.
    verify(inscriptions).suivi(ANNEE, java.util.Set.of(CLASSE_6E));
    verify(inscriptions)
        .inscriptionsParSemaine(eq(ANNEE), eq(java.util.Set.of(CLASSE_6E)), any(), any());
  }

  @Test
  void laCourbeCouvreLaFenetreDemandee() throws Exception {
    direction(PeriodeCourbe.JOURS_30, null);
    verify(inscriptions)
        .inscriptionsParSemaine(
            eq(ANNEE),
            anyCollection(),
            eq(LocalDate.of(2026, 10, 6)),
            eq(LocalDate.of(2026, 11, 4)));

    direction(PeriodeCourbe.JOURS_90, null);
    verify(inscriptions)
        .inscriptionsParSemaine(
            eq(ANNEE),
            anyCollection(),
            eq(LocalDate.of(2026, 8, 7)),
            eq(LocalDate.of(2026, 11, 4)));

    // Année : trois mois avant la rentrée (les inscriptions précèdent le début des cours).
    direction(PeriodeCourbe.ANNEE, null);
    verify(inscriptions)
        .inscriptionsParSemaine(
            eq(ANNEE),
            anyCollection(),
            eq(LocalDate.of(2026, 7, 5)),
            eq(LocalDate.of(2026, 11, 4)));
  }

  @Test
  void leSecondAppelVientDuCacheSansRecalcul() throws Exception {
    TableauDeBord premier = direction(PeriodeCourbe.ANNEE, null);
    TableauDeBord second = direction(PeriodeCourbe.ANNEE, null);

    assertThat(second).isSameAs(premier);
    assertThat(cache.ecritures).isEqualTo(1);
    verify(inscriptions).effectifs(ANNEE);
    // Un autre filtre est une autre entrée.
    direction(PeriodeCourbe.ANNEE, CodeCycle.MOYEN);
    assertThat(cache.ecritures).isEqualTo(2);
  }

  @Test
  void lEntreeDuCacheEstPropreAUneEcole() throws Exception {
    direction(PeriodeCourbe.ANNEE, null);
    UUID autre = UUID.fromString("01930000-0000-7000-8000-0000000000b2");

    ContexteTenant.executer(autre, () -> service.direction(PeriodeCourbe.ANNEE, null));

    // Une autre école ne lit pas l'entrée de la première : elle recalcule et écrit la sienne.
    assertThat(cache.ecritures).isEqualTo(2);
    assertThat(cache.valeurs).hasSize(2);
  }

  @Test
  void sansAnneeEnCoursToutEstVideSansInterrogerLesInscriptions() throws Exception {
    when(referentiel.anneeEnCours()).thenReturn(Optional.empty());

    TableauDeBord t = direction(PeriodeCourbe.ANNEE, null);

    assertThat(t.anneeId()).isNull();
    assertThat(t.inscrits()).isZero();
    assertThat(t.parStatut()).hasSize(5).allSatisfy(s -> assertThat(s.nombre()).isZero());
    assertThat(t.dossiers()).isEqualTo(new Part(0, 0, 0));
    assertThat(t.courbe()).isEmpty();
    verify(inscriptions, never()).effectifs(any());
    assertThat(cache.ecritures).isZero();
  }

  @Test
  void lePourcentageEstArrondiEtJamaisUneDivisionParZero() {
    assertThat(Part.de(0, 0).pourcentage()).isZero();
    assertThat(Part.de(3, 1).pourcentage()).isEqualTo(33);
    assertThat(Part.de(3, 2).pourcentage()).isEqualTo(67);
    assertThat(Part.de(8, 1).pourcentage()).isEqualTo(13); // 12,5 arrondi vers le haut
    assertThat(Part.de(10, 10).pourcentage()).isEqualTo(100);
  }

  @Test
  void laFinDeLaCourbeNeDepassePasLaFinDeLAnneeNiLeDebut() {
    AnneeEnCours annee =
        new AnneeEnCours(ANNEE, "2025-2026", LocalDate.of(2025, 10, 6), LocalDate.of(2026, 7, 3));
    LocalDate aujourdhui = LocalDate.of(2026, 11, 4);
    LocalDate debut = TableauDeBordService.debutCourbe(PeriodeCourbe.ANNEE, annee, aujourdhui);
    assertThat(TableauDeBordService.finCourbe(PeriodeCourbe.ANNEE, annee, aujourdhui, debut))
        .isEqualTo(LocalDate.of(2026, 7, 3));

    // Année qui commence dans plus de trois mois : plage réduite à un jour, jamais négative.
    AnneeEnCours future =
        new AnneeEnCours(ANNEE, "2027-2028", LocalDate.of(2027, 10, 4), LocalDate.of(2028, 7, 3));
    LocalDate debutFutur =
        TableauDeBordService.debutCourbe(PeriodeCourbe.ANNEE, future, aujourdhui);
    assertThat(TableauDeBordService.finCourbe(PeriodeCourbe.ANNEE, future, aujourdhui, debutFutur))
        .isEqualTo(debutFutur);
  }
}
