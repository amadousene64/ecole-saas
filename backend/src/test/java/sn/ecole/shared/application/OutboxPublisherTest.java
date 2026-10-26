package sn.ecole.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import sn.ecole.TestIntegration;
import sn.ecole.shared.domain.OutboxEvent;
import sn.ecole.shared.domain.OutboxEventRepository;

class OutboxPublisherTest extends TestIntegration {

  @Autowired OutboxPublisher publisher;
  @Autowired OutboxEventRepository outbox;
  @Autowired ExecutionPourTenant execution;

  private Map<String, Object> ligne(UUID id) {
    return superUtilisateur()
        .queryForMap(
            "SELECT tenant_id, type_evenement, charge->>'motif' AS motif FROM outbox_event WHERE id = ?",
            id);
  }

  @Test
  void lEvenementEstEcritDansLaTransactionAvecLEcoleCourante() {
    UUID agregat = UUID.randomUUID();
    OutboxEvent evenement =
        execution.executer(
            ECOLE_A,
            () -> publisher.publier("Billet", agregat, "BilletEmis", Map.of("motif", "retard")));

    assertThat(ligne(evenement.getId()))
        .containsEntry("tenant_id", ECOLE_A)
        .containsEntry("type_evenement", "BilletEmis")
        .containsEntry("motif", "retard");
  }

  @Test
  void unRollbackDuMetierAnnuleLEvenement() {
    UUID agregat = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                execution.executer(
                    ECOLE_A,
                    () -> {
                      publisher.publier("Billet", agregat, "BilletEmis", Map.of("motif", "x"));
                      throw new IllegalStateException("échec métier");
                    }))
        .isInstanceOf(IllegalStateException.class);

    Integer lignes =
        superUtilisateur()
            .queryForObject(
                "SELECT count(*) FROM outbox_event WHERE id_agregat = ?", Integer.class, agregat);
    assertThat(lignes).isZero();
  }

  @Test
  void publierHorsTransactionEstInterdit() {
    assertThatThrownBy(() -> publisher.publier("Billet", UUID.randomUUID(), "BilletEmis", Map.of()))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void leRelaisNeVoitQueLesEvenementsDeSonEcole() {
    UUID agregat = UUID.randomUUID();
    execution.executer(
        ECOLE_B, () -> publisher.publier("Paiement", agregat, "PaiementRecu", Map.of()));

    assertThat(execution.lire(ECOLE_A, outbox::findTop100ByTraiteLeIsNullOrderByCreeLeAsc))
        .noneMatch(e -> e.getIdAgregat().equals(agregat));
    assertThat(execution.lire(ECOLE_B, outbox::findTop100ByTraiteLeIsNullOrderByCreeLeAsc))
        .anyMatch(e -> e.getIdAgregat().equals(agregat));
  }

  @Test
  void leTraitementEtLesEchecsSontEnregistres() {
    UUID agregat = UUID.randomUUID();
    OutboxEvent cree =
        execution.executer(
            ECOLE_A, () -> publisher.publier("Sms", agregat, "SmsAEnvoyer", Map.of()));

    execution.executer(
        ECOLE_A,
        () ->
            outbox.findTop100ByTraiteLeIsNullOrderByCreeLeAsc().stream()
                .filter(e -> e.getId().equals(cree.getId()))
                .forEach(
                    e -> {
                      e.enregistrerEchec("passerelle SMS indisponible");
                      e.marquerTraite(java.time.Instant.now());
                    }));

    Map<String, Object> apres =
        superUtilisateur()
            .queryForMap(
                "SELECT tentatives, derniere_erreur, traite_le, version FROM outbox_event WHERE id = ?",
                cree.getId());
    assertThat(apres)
        .containsEntry("tentatives", 1)
        .containsEntry("derniere_erreur", "passerelle SMS indisponible")
        .containsEntry("version", 1);
    assertThat(apres.get("traite_le")).isNotNull();
  }
}
