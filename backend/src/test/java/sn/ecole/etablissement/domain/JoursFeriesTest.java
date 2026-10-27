package sn.ecole.etablissement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JoursFeriesTest {

  static Map<Integer, LocalDate> dimanchesDePaques() {
    return Map.of(
        2024, LocalDate.of(2024, 3, 31),
        2025, LocalDate.of(2025, 4, 20),
        2026, LocalDate.of(2026, 4, 5),
        2027, LocalDate.of(2027, 3, 28),
        2028, LocalDate.of(2028, 4, 16),
        2029, LocalDate.of(2029, 4, 1));
  }

  @ParameterizedTest
  @MethodSource("annees")
  void paquesEstCalculeCorrectement(Map.Entry<Integer, LocalDate> attendu) {
    assertThat(JoursFeries.paques(attendu.getKey())).isEqualTo(attendu.getValue());
  }

  static java.util.stream.Stream<Map.Entry<Integer, LocalDate>> annees() {
    return dimanchesDePaques().entrySet().stream();
  }

  @Test
  void lesFeriesDe2026_2027ContiennentLesFeteFixesEtMobiles() {
    List<JoursFeries.Ferie> feries =
        JoursFeries.pour(LocalDate.of(2026, 10, 5), LocalDate.of(2027, 7, 2));

    assertThat(feries)
        .extracting(JoursFeries.Ferie::libelle, JoursFeries.Ferie::date)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Toussaint", LocalDate.of(2026, 11, 1)),
            org.assertj.core.groups.Tuple.tuple("Noël", LocalDate.of(2026, 12, 25)),
            org.assertj.core.groups.Tuple.tuple("Jour de l'An", LocalDate.of(2027, 1, 1)),
            org.assertj.core.groups.Tuple.tuple("Lundi de Pâques", LocalDate.of(2027, 3, 29)),
            org.assertj.core.groups.Tuple.tuple("Fête de l'Indépendance", LocalDate.of(2027, 4, 4)),
            org.assertj.core.groups.Tuple.tuple("Fête du Travail", LocalDate.of(2027, 5, 1)),
            org.assertj.core.groups.Tuple.tuple("Ascension", LocalDate.of(2027, 5, 6)),
            org.assertj.core.groups.Tuple.tuple("Lundi de Pentecôte", LocalDate.of(2027, 5, 17)));
  }

  @Test
  void lAssomptionEstExclueSiElleTombeHorsDeLAnnee() {
    assertThat(JoursFeries.pour(LocalDate.of(2026, 10, 5), LocalDate.of(2027, 7, 2)))
        .noneMatch(f -> f.libelle().equals("Assomption"));
  }

  @Test
  void lesBornesSontIncluses() {
    assertThat(JoursFeries.pour(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 1)))
        .extracting(JoursFeries.Ferie::libelle)
        .containsExactly("Jour de l'An");
  }
}
