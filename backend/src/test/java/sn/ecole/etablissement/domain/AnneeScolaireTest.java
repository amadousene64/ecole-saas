package sn.ecole.etablissement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import sn.ecole.shared.domain.ErreurMetier;

class AnneeScolaireTest {

  private static AnneeScolaire annee() {
    return AnneeScolaire.creer("2026-2027", LocalDate.of(2026, 10, 5), LocalDate.of(2027, 7, 2));
  }

  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void uneAnneeNaitEnPreparation() {
    assertThat(annee().getStatut()).isEqualTo(StatutAnnee.PREPARATION);
  }

  @Test
  void leLibelleDoitEtreDeuxAnneesConsecutives() {
    assertCode(
        () -> AnneeScolaire.creer("2026-2028", LocalDate.of(2026, 10, 5), LocalDate.of(2028, 7, 1)),
        "LIBELLE_ANNEE_INVALIDE");
    assertCode(
        () -> AnneeScolaire.creer("2026/2027", LocalDate.of(2026, 10, 5), LocalDate.of(2027, 7, 1)),
        "LIBELLE_ANNEE_INVALIDE");
  }

  @Test
  void lesDatesCorrespondentAuLibelleEtSontOrdonnees() {
    assertCode(
        () -> AnneeScolaire.creer("2026-2027", LocalDate.of(2025, 10, 5), LocalDate.of(2027, 7, 1)),
        "DATES_ANNEE_INVALIDES");
    assertCode(
        () ->
            AnneeScolaire.creer("2026-2027", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 12, 1)),
        "DATES_ANNEE_INVALIDES");
    assertCode(
        () -> AnneeScolaire.creer("2026-2027", LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 1)),
        "DATES_ANNEE_INVALIDES");
  }

  @Test
  void cycleDeVieNormal() {
    AnneeScolaire annee = annee();
    annee.ouvrir();
    assertThat(annee.getStatut()).isEqualTo(StatutAnnee.EN_COURS);
    annee.cloturer();
    assertThat(annee.getStatut()).isEqualTo(StatutAnnee.CLOTUREE);
  }

  @Test
  void lesTransitionsInterditesSontRefusees() {
    AnneeScolaire preparation = annee();
    assertCode(preparation::cloturer, "TRANSITION_ANNEE_INTERDITE");
    preparation.ouvrir();
    assertCode(preparation::ouvrir, "TRANSITION_ANNEE_INTERDITE");
    preparation.cloturer();
    assertCode(preparation::ouvrir, "TRANSITION_ANNEE_INTERDITE");
    assertCode(preparation::cloturer, "TRANSITION_ANNEE_INTERDITE");
  }

  @Test
  void uneAnneeCloturee_estEnLectureSeule() {
    AnneeScolaire annee = annee();
    annee.ouvrir();
    annee.cloturer();
    assertCode(annee::verifierModifiable, "ANNEE_CLOTUREE");
    assertCode(
        () -> annee.modifier("2026-2027", LocalDate.of(2026, 10, 6), LocalDate.of(2027, 7, 2)),
        "ANNEE_CLOTUREE");
  }

  @Test
  void uneAnneeEnCoursSeModifieMaisNeSeSupprimePas() {
    AnneeScolaire annee = annee();
    annee.ouvrir();
    annee.modifier("2026-2027", LocalDate.of(2026, 10, 6), LocalDate.of(2027, 7, 3));
    assertThat(annee.getDateDebut()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertCode(() -> annee.supprimer(Instant.now()), "ANNEE_NON_SUPPRIMABLE");
  }

  @Test
  void uneAnneeEnPreparationPeutEtreSupprimeeLogiquement() {
    AnneeScolaire annee = annee();
    annee.supprimer(Instant.now());
    assertThat(annee.estSupprime()).isTrue();
  }

  @Test
  void contientLesBornesIncluses() {
    AnneeScolaire annee = annee();
    assertThat(annee.contient(LocalDate.of(2026, 10, 5))).isTrue();
    assertThat(annee.contient(LocalDate.of(2027, 7, 2))).isTrue();
    assertThat(annee.contient(LocalDate.of(2026, 10, 4))).isFalse();
    assertThat(annee.contient(LocalDate.of(2027, 7, 3))).isFalse();
  }
}
