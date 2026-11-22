package sn.ecole.etablissement.domain;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.shared.domain.ErreurMetier;

class MatiereEtCreneauTest {

  private static final UUID CYCLE = UUID.fromString("01930000-0000-7000-8000-00000000c001");

  private static Creneau creneau(String debut, String fin, DayOfWeek... jours) {
    return Creneau.creer(
        CYCLE, LocalTime.parse(debut), LocalTime.parse(fin), List.of(jours), null, null);
  }

  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void lesJoursSeRelisentTelsQuels() {
    Creneau c = creneau("08:00", "10:00", MONDAY, WEDNESDAY, SATURDAY, SUNDAY);
    assertThat(c.getJours()).containsExactly(MONDAY, WEDNESDAY, SATURDAY, SUNDAY);
    assertThat(c.getType()).isEqualTo(TypeCreneau.COURS);
  }

  @Test
  void deuxCreneauxSeChevauchentSeulementUnMemeJourEtSurUnMemeHoraire() {
    Creneau matin = creneau("08:00", "10:00", MONDAY, FRIDAY);
    assertThat(matin.chevauche(creneau("09:59", "11:00", FRIDAY))).isTrue();
    assertThat(matin.chevauche(creneau("07:00", "12:00", MONDAY))).isTrue();
    assertThat(matin.chevauche(creneau("10:00", "11:00", MONDAY))).isFalse();
    assertThat(matin.chevauche(creneau("07:00", "08:00", MONDAY))).isFalse();
    assertThat(matin.chevauche(creneau("08:00", "10:00", WEDNESDAY))).isFalse();
  }

  @Test
  void lesSecondesSontIgnoreesEtLesBornesControlees() {
    Creneau c =
        Creneau.creer(
            CYCLE, LocalTime.of(8, 0, 30), LocalTime.of(9, 0, 59), List.of(MONDAY), null, " ");
    assertThat(c.getDebut()).isEqualTo(LocalTime.of(8, 0));
    assertThat(c.getFin()).isEqualTo(LocalTime.of(9, 0));
    assertThat(c.getLibelle()).isNull();
    assertCode(() -> creneau("05:59", "08:00", MONDAY), "HORAIRE_INVALIDE");
    assertCode(() -> creneau("19:00", "20:01", MONDAY), "HORAIRE_INVALIDE");
    assertCode(() -> creneau("08:00", "08:14", MONDAY), "HORAIRE_INVALIDE");
    assertCode(() -> creneau("08:00", "09:00"), "JOURS_REQUIS");
  }

  @Test
  void laCopieGardeHoraireJoursEtType() {
    UUID autre = UUID.fromString("01930000-0000-7000-8000-00000000c002");
    Creneau pause =
        Creneau.creer(
            CYCLE,
            LocalTime.of(10, 0),
            LocalTime.of(10, 15),
            List.of(MONDAY, FRIDAY),
            TypeCreneau.PAUSE,
            "Récréation");
    Creneau copie = pause.copierVers(autre);
    assertThat(copie.getCycleId()).isEqualTo(autre);
    assertThat(copie.getJours()).containsExactly(MONDAY, FRIDAY);
    assertThat(copie.getType()).isEqualTo(TypeCreneau.PAUSE);
    assertThat(copie.getLibelle()).isEqualTo("Récréation");
  }

  @Test
  void uneMatiereAAuMoinsUnCycleRangesDansLOrdreDuReferentiel() {
    Matiere m =
        Matiere.creer(" svt ", "SVT", "", List.of(CodeCycle.SECONDAIRE, CodeCycle.MOYEN), 90);
    assertThat(m.getCode()).isEqualTo("SVT");
    assertThat(m.getGroupe()).isNull();
    assertThat(m.getCycles()).containsExactly(CodeCycle.MOYEN, CodeCycle.SECONDAIRE);
    assertCode(() -> Matiere.creer("X", "X", null, List.of(), 0), "CYCLES_REQUIS");
    assertCode(
        () -> Matiere.creer("X", "X", null, List.of(CodeCycle.MOYEN), 1000), "CHAMP_INVALIDE");
    assertCode(
        () -> Matiere.creer("X-1", "X", null, List.of(CodeCycle.MOYEN), 0), "CHAMP_INVALIDE");
  }

  @Test
  void leModeleSenegalaisAdesCodesUniquesEtValides() {
    assertThat(ModeleSenegalais.MATIERES)
        .extracting(ModeleSenegalais.ModeleMatiere::code)
        .doesNotHaveDuplicates();
    ModeleSenegalais.MATIERES.forEach(
        m -> Matiere.creer(m.code(), m.libelle(), m.groupe(), m.cycles(), m.ordre()));
  }
}
