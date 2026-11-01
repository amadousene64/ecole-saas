package sn.ecole.inscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.inscription.StatistiquesInscription.InscriptionsSemaine;
import sn.ecole.inscription.StatistiquesInscription.SuiviClasses;
import sn.ecole.inscription.domain.StatistiquesInscriptionRepository;
import sn.ecole.inscription.domain.StatistiquesInscriptionRepository.DateEtNombre;
import sn.ecole.shared.domain.ErreurMetier;

/** Regroupement par semaine et garde-fous des agrégats d'inscription. */
class StatistiquesInscriptionServiceTest {

  private static final UUID ANNEE = UUID.fromString("01930000-0000-7000-8000-00000000aa01");
  private static final Set<UUID> CLASSES =
      Set.of(UUID.fromString("01930000-0000-7000-8000-00000000d001"));

  private final StatistiquesInscriptionRepository depot =
      mock(StatistiquesInscriptionRepository.class);
  private final StatistiquesInscriptionService service = new StatistiquesInscriptionService(depot);

  @Test
  void regroupeParSemaineEtGardeLesSemainesVides() {
    // Du mercredi 4 novembre au mardi 24 novembre 2026 : quatre lundis (2, 9, 16, 23).
    when(depot.entreesParJour(any(), anyCollection(), any(), any()))
        .thenReturn(
            List.of(
                new DateEtNombre(LocalDate.of(2026, 11, 4), 2), // mercredi, semaine du 2
                new DateEtNombre(LocalDate.of(2026, 11, 8), 1), // dimanche, semaine du 2
                new DateEtNombre(LocalDate.of(2026, 11, 9), 5), // lundi, semaine du 9
                new DateEtNombre(LocalDate.of(2026, 11, 24), 3))); // mardi, semaine du 23

    List<InscriptionsSemaine> semaines =
        service.inscriptionsParSemaine(
            ANNEE, CLASSES, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 24));

    assertThat(semaines)
        .containsExactly(
            new InscriptionsSemaine(LocalDate.of(2026, 11, 2), 3),
            new InscriptionsSemaine(LocalDate.of(2026, 11, 9), 5),
            new InscriptionsSemaine(LocalDate.of(2026, 11, 16), 0),
            new InscriptionsSemaine(LocalDate.of(2026, 11, 23), 3));
  }

  @Test
  void sansClasseLaCourbeEstNulleSansInterrogerLaBase() {
    List<InscriptionsSemaine> semaines =
        service.inscriptionsParSemaine(
            ANNEE, Set.of(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9));

    assertThat(semaines).extracting(InscriptionsSemaine::nombre).containsExactly(0L, 0L);
    assertThat(service.suivi(ANNEE, Set.of())).isEqualTo(new SuiviClasses(0, 0, 0, 0));
    verifyNoInteractions(depot);
  }

  @Test
  void refuseUnePlageInverseeOuTropLongue() {
    assertThatThrownBy(
            () ->
                service.inscriptionsParSemaine(
                    ANNEE, CLASSES, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 2)))
        .isInstanceOf(ErreurMetier.class)
        .extracting(e -> ((ErreurMetier) e).code())
        .isEqualTo("PLAGE_INVALIDE");
    assertThatThrownBy(
            () ->
                service.inscriptionsParSemaine(
                    ANNEE, CLASSES, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 11, 2)))
        .isInstanceOf(ErreurMetier.class);
  }

  @Test
  void leSuiviInterrogeLeDepotPourLesClassesDemandees() {
    when(depot.suivi(ANNEE, CLASSES)).thenReturn(new SuiviClasses(10, 7, 8, 2));

    assertThat(service.suivi(ANNEE, CLASSES)).isEqualTo(new SuiviClasses(10, 7, 8, 2));
    verify(depot).suivi(ANNEE, CLASSES);
  }
}
