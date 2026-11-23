package sn.ecole.personnel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.shared.domain.ErreurMetier;

class AffectationTest {

  private static final UUID ANNEE = UUID.fromString("01930000-0000-7000-8000-0000000000a1");
  private static final UUID PROF = UUID.fromString("01930000-0000-7000-8000-0000000000b1");
  private static final UUID CLASSE = UUID.fromString("01930000-0000-7000-8000-0000000000c1");
  private static final UUID MATIERE = UUID.fromString("01930000-0000-7000-8000-0000000000d1");
  private static final LocalDate RENTREE = LocalDate.of(2026, 10, 5);

  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void laPeriodeEstSemiOuverte() {
    Affectation a =
        Affectation.enseignement(ANNEE, PROF, CLASSE, MATIERE, RENTREE, RENTREE.plusDays(10));
    assertThat(a.estEnCoursLe(RENTREE.minusDays(1))).isFalse();
    assertThat(a.estEnCoursLe(RENTREE)).isTrue();
    assertThat(a.estEnCoursLe(RENTREE.plusDays(9))).isTrue();
    assertThat(a.estEnCoursLe(RENTREE.plusDays(10))).as("la fin ne vaut plus").isFalse();
    assertThat(
            Affectation.titulaire(ANNEE, PROF, CLASSE, RENTREE, null)
                .estEnCoursLe(LocalDate.of(2030, 1, 1)))
        .isTrue();
  }

  @Test
  void unRemplacementALaDateDeFinNeChevauchePas() {
    Affectation a =
        Affectation.enseignement(ANNEE, PROF, CLASSE, MATIERE, RENTREE, RENTREE.plusDays(10));
    assertThat(a.chevauche(RENTREE.plusDays(10), null)).isFalse();
    assertThat(a.chevauche(RENTREE.plusDays(9), null)).isTrue();
    assertThat(a.chevauche(RENTREE.minusDays(5), RENTREE)).isFalse();
    assertThat(a.chevauche(RENTREE.minusDays(5), RENTREE.plusDays(1))).isTrue();
    Affectation sansFin = Affectation.titulaire(ANNEE, PROF, CLASSE, RENTREE, null);
    assertThat(sansFin.chevauche(LocalDate.of(2027, 6, 1), null)).isTrue();
  }

  @Test
  void terminerAvanceLaFinMaisNeLaRepoussePas() {
    Affectation a = Affectation.surveillance(ANNEE, PROF, CodeCycle.MOYEN, RENTREE, null);
    a.terminer(RENTREE.plusDays(30));
    assertThat(a.getFin()).isEqualTo(RENTREE.plusDays(30));
    a.terminer(RENTREE.plusDays(20));
    assertThat(a.getFin()).isEqualTo(RENTREE.plusDays(20));
    assertCode(() -> a.terminer(RENTREE.plusDays(25)), "AFFECTATION_TERMINEE");
    assertCode(() -> a.terminer(RENTREE), "PERIODE_INVALIDE");
  }

  @Test
  void laPeriodeEstControleeEtUneAnnulationSupprimeLogiquement() {
    assertCode(
        () -> Affectation.titulaire(ANNEE, PROF, CLASSE, RENTREE, RENTREE), "PERIODE_INVALIDE");
    assertCode(() -> Affectation.titulaire(ANNEE, PROF, CLASSE, null, null), "PERIODE_INVALIDE");
    Affectation a = Affectation.titulaire(ANNEE, PROF, CLASSE, RENTREE, null);
    a.changerPeriode(RENTREE.plusDays(1), RENTREE.plusDays(2));
    assertThat(a.getDebut()).isEqualTo(RENTREE.plusDays(1));
    a.marquerAnnulee(Instant.parse("2026-11-04T10:00:00Z"));
    assertThat(a.estSupprime()).isTrue();
  }

  @Test
  void lAnnuaireNEcritQueSiLesNomsChangentOuUnJourApres() {
    Instant t = Instant.parse("2026-11-04T10:00:00Z");
    MembrePersonnel m = MembrePersonnel.creer(PROF, " Moussa ", "Fictif", t);
    assertThat(m.getPrenoms()).isEqualTo("Moussa");
    assertThat(m.aJour("Moussa", "Fictif", t.plusSeconds(3600))).isTrue();
    assertThat(m.aJour("Moussa", "Autre", t.plusSeconds(3600))).isFalse();
    assertThat(m.aJour("Moussa", "Fictif", t.plusSeconds(25 * 3600))).isFalse();
    assertThat(m.renseigner("", null, t)).isTrue();
    assertThat(m.getPrenoms()).isNull();
  }
}
