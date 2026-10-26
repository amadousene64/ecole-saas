package sn.ecole.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;
import static sn.ecole.Fixtures.ECOLE_INACTIVE;
import static sn.ecole.Fixtures.SURVEILLANT_B;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import sn.ecole.TestIntegration;
import sn.ecole.shared.domain.ContexteTenant;

class ExecutionPourTenantTest extends TestIntegration {

  @Autowired ExecutionPourTenant execution;
  @Autowired JdbcTemplate jdbc;

  private List<UUID> utilisateurs() {
    return jdbc.queryForList("SELECT utilisateur_id FROM appartenance", UUID.class);
  }

  @Test
  void executerPositionneLEcolePourLaTransaction() {
    assertThat(execution.lire(ECOLE_A, this::utilisateurs))
        .contains(DIRECTEUR_A)
        .doesNotContain(SURVEILLANT_B);
    assertThat(execution.executer(ECOLE_B, this::utilisateurs))
        .contains(SURVEILLANT_B)
        .doesNotContain(DIRECTEUR_A);
  }

  @Test
  void leContexteNExistePlusApresLExecution() {
    execution.executer(ECOLE_A, () -> assertThat(ContexteTenant.courant()).contains(ECOLE_A));
    assertThat(ContexteTenant.courant()).isEmpty();
  }

  @Test
  void pourChaqueEcoleParcourtLesEcolesActivesChacuneIsolee() {
    Map<UUID, List<UUID>> vusParEcole = new ConcurrentHashMap<>();

    BilanExecution bilan =
        execution.pourChaqueEcole("essai", ecole -> vusParEcole.put(ecole, utilisateurs()));

    assertThat(bilan.toutReussi()).isTrue();
    assertThat(bilan.reussies()).contains(ECOLE_A, ECOLE_B).doesNotContain(ECOLE_INACTIVE);
    assertThat(vusParEcole.get(ECOLE_A)).contains(DIRECTEUR_A).doesNotContain(SURVEILLANT_B);
    assertThat(vusParEcole.get(ECOLE_B)).contains(SURVEILLANT_B).doesNotContain(DIRECTEUR_A);
  }

  @Test
  void lEchecDUneEcoleEstRapporteSansBloquerLesAutres() {
    BilanExecution bilan =
        execution.pourChaqueEcole(
            "essai-echec",
            ecole -> {
              if (ecole.equals(ECOLE_A)) {
                throw new IllegalStateException("panne simulée");
              }
            });

    assertThat(bilan.toutReussi()).isFalse();
    assertThat(bilan.echouees()).containsExactly(ECOLE_A);
    assertThat(bilan.reussies()).contains(ECOLE_B);
  }

  @Test
  void changerDEcoleDansUnContexteOuvertEstInterdit() {
    assertThatThrownBy(
            () -> execution.executer(ECOLE_A, () -> execution.executer(ECOLE_B, () -> {})))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> execution.executer(ECOLE_A, () -> execution.pourChaqueEcole("x", e -> {})))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reentrerDansLaMemeEcoleEstPermis() {
    assertThat(execution.executer(ECOLE_A, () -> execution.lire(ECOLE_A, this::utilisateurs)))
        .contains(DIRECTEUR_A);
  }
}
