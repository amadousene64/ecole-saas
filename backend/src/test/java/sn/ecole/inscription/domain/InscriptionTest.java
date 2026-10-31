package sn.ecole.inscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.shared.domain.ErreurMetier;

class InscriptionTest {

  private static final LocalDate RENTREE = LocalDate.of(2026, 10, 5);

  private static Inscription inscription(StatutInscription statut) {
    return Inscription.creer(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), statut, false, RENTREE);
  }

  private static void code(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void uneInscriptionCommencePreInscriteOuInscrite() {
    assertThat(inscription(StatutInscription.PRE_INSCRIT).active()).isTrue();
    code(() -> inscription(StatutInscription.RADIE), "STATUT_INVALIDE");
    code(() -> inscription(null), "STATUT_INVALIDE");
  }

  @Test
  void validationPuisSortie() {
    Inscription i = inscription(StatutInscription.PRE_INSCRIT);
    i.valider();
    assertThat(i.getStatut()).isEqualTo(StatutInscription.INSCRIT);
    code(i::valider, "TRANSITION_INTERDITE");
    i.sortir(StatutInscription.TRANSFERE, RENTREE.plusMonths(2), " Mutation des parents ");
    assertThat(i.active()).isFalse();
    assertThat(i.getMotifSortie()).isEqualTo("Mutation des parents");
    code(
        () -> i.sortir(StatutInscription.ABANDON, RENTREE.plusMonths(3), null),
        "INSCRIPTION_TERMINEE");
    code(() -> i.changerClasse(UUID.randomUUID()), "INSCRIPTION_TERMINEE");
    code(() -> i.definirRedoublant(true), "INSCRIPTION_TERMINEE");
  }

  @Test
  void unPreInscritNePeutQuAbandonner() {
    Inscription i = inscription(StatutInscription.PRE_INSCRIT);
    code(() -> i.sortir(StatutInscription.RADIE, RENTREE, null), "TRANSITION_INTERDITE");
    code(() -> i.sortir(StatutInscription.TRANSFERE, RENTREE, null), "TRANSITION_INTERDITE");
    i.sortir(StatutInscription.ABANDON, RENTREE, null);
    assertThat(i.getStatut()).isEqualTo(StatutInscription.ABANDON);
  }

  @Test
  void laSortieExigeUneDateValideEtUnStatutDeSortie() {
    Inscription i = inscription(StatutInscription.INSCRIT);
    code(() -> i.sortir(StatutInscription.RADIE, null, null), "DATE_SORTIE_INVALIDE");
    code(
        () -> i.sortir(StatutInscription.RADIE, RENTREE.minusDays(1), null),
        "DATE_SORTIE_INVALIDE");
    code(() -> i.sortir(StatutInscription.INSCRIT, RENTREE, null), "STATUT_INVALIDE");
    code(() -> i.sortir(StatutInscription.RADIE, RENTREE, "x".repeat(201)), "CHAMP_INVALIDE");
  }

  @Test
  void lesOptionsDoiventEtreProposeesEtUneParGroupe() {
    UUID esp = UUID.randomUUID();
    UUID ar = UUID.randomUUID();
    UUID arts = UUID.randomUUID();
    Map<UUID, String> proposees = Map.of(esp, "LV2", ar, "LV2", arts, "ARTS");
    InscriptionOption.verifierChoix(List.of(esp, arts), proposees);
    InscriptionOption.verifierChoix(List.of(), proposees);
    code(
        () -> InscriptionOption.verifierChoix(List.of(esp, ar), proposees),
        "OPTIONS_INCOMPATIBLES");
    code(
        () -> InscriptionOption.verifierChoix(List.of(UUID.randomUUID()), proposees),
        "OPTION_NON_PROPOSEE");
  }
}
