package sn.ecole.etablissement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import sn.ecole.etablissement.TypePeriode;
import sn.ecole.shared.domain.ErreurMetier;

class ReglesPeriodesTest {

  private static final AnneeScolaire ANNEE =
      AnneeScolaire.creer("2026-2027", LocalDate.of(2026, 10, 5), LocalDate.of(2027, 7, 2));

  private static PeriodePrevue p(int numero, String debut, String fin) {
    return new PeriodePrevue(numero, LocalDate.parse(debut), LocalDate.parse(fin));
  }

  private static void assertCode(List<PeriodePrevue> periodes, String code) {
    assertThatThrownBy(() -> ReglesPeriodes.verifier(ANNEE, periodes))
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @ParameterizedTest
  @EnumSource(TypePeriode.class)
  void lesPeriodesGenereesCouvrentToutesLAnneeSansTrouNiChevauchement(TypePeriode type) {
    List<PeriodePrevue> periodes = ReglesPeriodes.generer(ANNEE, type);

    assertThat(periodes).hasSize(type.nombre());
    assertThat(periodes.getFirst().debut()).isEqualTo(ANNEE.getDateDebut());
    assertThat(periodes.getLast().fin()).isEqualTo(ANNEE.getDateFin());
    for (int i = 1; i < periodes.size(); i++) {
      assertThat(periodes.get(i).debut()).isEqualTo(periodes.get(i - 1).fin().plusDays(1));
    }
    assertThat(periodes.stream().map(PeriodePrevue::numero).toList())
        .isEqualTo(Stream.iterate(1, n -> n + 1).limit(type.nombre()).toList());
    assertThatCode(() -> ReglesPeriodes.verifier(ANNEE, periodes)).doesNotThrowAnyException();
  }

  @Test
  void leTotalDesJoursEstConserveSurUneAnneeBissextile() {
    AnneeScolaire bissextile =
        AnneeScolaire.creer("2027-2028", LocalDate.of(2027, 10, 4), LocalDate.of(2028, 7, 7));
    long total = ChronoUnit.DAYS.between(bissextile.getDateDebut(), bissextile.getDateFin()) + 1;

    List<PeriodePrevue> periodes = ReglesPeriodes.generer(bissextile, TypePeriode.TRIMESTRE);

    assertThat(
            periodes.stream().mapToLong(x -> ChronoUnit.DAYS.between(x.debut(), x.fin()) + 1).sum())
        .isEqualTo(total);
    // Les jours restants vont aux premiers blocs : écart d'un jour au plus.
    long min =
        periodes.stream()
            .mapToLong(x -> ChronoUnit.DAYS.between(x.debut(), x.fin()))
            .min()
            .orElseThrow();
    long max =
        periodes.stream()
            .mapToLong(x -> ChronoUnit.DAYS.between(x.debut(), x.fin()))
            .max()
            .orElseThrow();
    assertThat(max - min).isLessThanOrEqualTo(1);
  }

  @Test
  void lesLibellesSuiventLeType() {
    assertThat(TypePeriode.TRIMESTRE.libelle(1)).isEqualTo("1er trimestre");
    assertThat(TypePeriode.TRIMESTRE.libelle(3)).isEqualTo("3e trimestre");
    assertThat(TypePeriode.SEMESTRE.libelle(2)).isEqualTo("2e semestre");
  }

  @Test
  void desTrousEntrePeriodesSontPermis() {
    assertThatCode(
            () ->
                ReglesPeriodes.verifier(
                    ANNEE,
                    List.of(p(1, "2026-10-05", "2026-12-18"), p(2, "2027-01-05", "2027-07-02"))))
        .doesNotThrowAnyException();
  }

  @Test
  void unChevauchementEstRefuse() {
    assertCode(
        List.of(p(1, "2026-10-05", "2027-01-10"), p(2, "2027-01-10", "2027-07-02")),
        "PERIODES_CHEVAUCHANTES");
  }

  @Test
  void unNumeroSuperieurNePeutPasCommencerAvantLePrecedent() {
    assertCode(
        List.of(p(1, "2027-01-05", "2027-07-02"), p(2, "2026-10-05", "2026-12-18")),
        "PERIODES_CHEVAUCHANTES");
  }

  @Test
  void unePeriodeHorsDeLAnneeEstRefusee() {
    assertCode(List.of(p(1, "2026-09-01", "2026-12-18")), "PERIODE_HORS_ANNEE");
    assertCode(List.of(p(1, "2026-10-05", "2027-07-03")), "PERIODE_HORS_ANNEE");
  }

  @Test
  void desDatesInverseesOuUnNumeroEnDoubleSontRefuses() {
    assertCode(List.of(p(1, "2026-12-18", "2026-10-05")), "PERIODE_INVALIDE");
    assertCode(
        List.of(p(1, "2026-10-05", "2026-12-18"), p(1, "2027-01-05", "2027-03-01")),
        "PERIODE_EN_DOUBLE");
  }
}
