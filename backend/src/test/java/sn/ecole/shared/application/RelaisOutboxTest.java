package sn.ecole.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import sn.ecole.TestIntegration;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.OutboxEvent;

@Import(RelaisOutboxTest.Traitements.class)
class RelaisOutboxTest extends TestIntegration {

  /** Traitements d'essai : l'un réussit en notant l'école courante, l'autre échoue toujours. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Traitements {

    static final List<String> VUS = new CopyOnWriteArrayList<>();

    @Bean
    TraitementOutbox essaiReussi() {
      return new TraitementOutbox() {
        @Override
        public String typeEvenement() {
          return "EssaiReussi";
        }

        @Override
        public void traiter(OutboxEvent evenement) {
          VUS.add(ContexteTenant.exiger() + "/" + evenement.getIdAgregat());
        }
      };
    }

    @Bean
    TraitementOutbox essaiEchoue() {
      return new TraitementOutbox() {
        @Override
        public String typeEvenement() {
          return "EssaiEchoue";
        }

        @Override
        public void traiter(OutboxEvent evenement) {
          throw new IllegalStateException("panne, avec une donnée sensible : 77 000 00 00");
        }
      };
    }
  }

  @Autowired RelaisOutbox relais;
  @Autowired OutboxPublisher publisher;
  @Autowired ExecutionPourTenant execution;

  @BeforeEach
  void vider() {
    superUtilisateur().execute("TRUNCATE ecole.outbox_event");
    Traitements.VUS.clear();
  }

  private UUID publier(UUID ecole, String type) {
    UUID agregat = UUID.randomUUID();
    execution.executer(ecole, () -> publisher.publier("Essai", agregat, type, Map.of()));
    return agregat;
  }

  private Map<String, Object> ligne(UUID agregat) {
    return superUtilisateur()
        .queryForMap(
            "SELECT traite_le, tentatives, derniere_erreur, prochaine_tentative_le > now() AS"
                + " differe FROM ecole.outbox_event WHERE id_agregat = ?",
            agregat);
  }

  @Test
  void chaqueEvenementEstTraiteUneFoisDansLeContexteDeSonEcole() {
    UUID a = publier(ECOLE_A, "EssaiReussi");
    UUID b = publier(ECOLE_B, "EssaiReussi");

    assertThat(relais.traiterTout()).isEqualTo(2);
    assertThat(Traitements.VUS).containsExactlyInAnyOrder(ECOLE_A + "/" + a, ECOLE_B + "/" + b);
    assertThat(ligne(a).get("traite_le")).isNotNull();

    assertThat(relais.traiterTout()).isZero();
    assertThat(Traitements.VUS).hasSize(2);
  }

  @Test
  void unEchecEstConsigneSansSonMessageEtRetenteApresUnDelai() {
    UUID agregat = publier(ECOLE_A, "EssaiEchoue");

    assertThat(relais.traiterTout()).isZero();
    Map<String, Object> apres = ligne(agregat);
    assertThat(apres.get("traite_le")).isNull();
    assertThat(apres.get("tentatives")).isEqualTo(1);
    assertThat(apres.get("derniere_erreur")).isEqualTo("IllegalStateException");
    assertThat(apres.get("differe")).isEqualTo(true);

    // Pas de nouvelle tentative avant l'échéance.
    relais.traiterTout();
    assertThat(ligne(agregat).get("tentatives")).isEqualTo(1);
  }

  @Test
  void unTypeSansTraitementResteEnAttenteAvecUneErreur() {
    UUID agregat = publier(ECOLE_A, "TypeInconnu");
    relais.traiterTout();
    assertThat(ligne(agregat).get("tentatives")).isEqualTo(1);
    assertThat(ligne(agregat).get("traite_le")).isNull();
  }

  @Test
  void auDelaDuPlafondDeTentativesLEvenementNEstPlusRepris() {
    UUID agregat = publier(ECOLE_A, "EssaiReussi");
    superUtilisateur()
        .update(
            "UPDATE ecole.outbox_event SET tentatives = ? WHERE id_agregat = ?",
            RelaisOutbox.TENTATIVES_MAX,
            agregat);
    assertThat(relais.traiterTout()).isZero();
    assertThat(Traitements.VUS).isEmpty();
  }

  @Test
  void leDelaiDoubleEtEstPlafonne() {
    assertThat(RelaisOutbox.delai(0)).isEqualTo(Duration.ofSeconds(30));
    assertThat(RelaisOutbox.delai(1)).isEqualTo(Duration.ofSeconds(60));
    assertThat(RelaisOutbox.delai(9)).isEqualTo(Duration.ofHours(1));
  }
}
