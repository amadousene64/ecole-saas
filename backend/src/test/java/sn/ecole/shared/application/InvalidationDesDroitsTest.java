package sn.ecole.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import sn.ecole.shared.domain.AppartenanceRepository;
import sn.ecole.shared.domain.CacheRoles;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.TenantRepository;

/** Séquence d'invalidation autour du commit, sans base ni Redis. */
class InvalidationDesDroitsTest {

  private final SimpleMeterRegistry metriques = new SimpleMeterRegistry();
  private final AppartenanceRepository appartenances = mock(AppartenanceRepository.class);
  private final CacheRoles cache = mock(CacheRoles.class);
  private final AppartenanceService service =
      new AppartenanceService(
          appartenances,
          mock(TenantRepository.class),
          cache,
          mock(ExecutionPourTenant.class),
          mock(JournalAudit.class),
          metriques);
  private final UUID utilisateur = UUID.randomUUID();

  @BeforeEach
  void ouvrirTransactionSimulee() {
    TransactionSynchronizationManager.initSynchronization();
    when(appartenances.findByUtilisateurIdAndRole(any(), any())).thenReturn(Optional.empty());
  }

  @AfterEach
  void fermer() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  private void attribuerDansUneEcole() {
    ContexteTenant.executer(
        UUID.randomUUID(),
        () -> {
          service.attribuer(utilisateur, RoleEcole.PARENT);
          return null;
        });
  }

  @Test
  void laGenerationChangeAvantEtApresLeCommit() {
    attribuerDansUneEcole();
    verify(cache, times(0)).nouvelleGeneration(utilisateur);

    TransactionSynchronizationUtils.triggerBeforeCommit(false);
    verify(cache, times(1)).nouvelleGeneration(utilisateur);
    TransactionSynchronizationUtils.triggerAfterCommit();
    verify(cache, times(2)).nouvelleGeneration(utilisateur);
    assertThat(echecs()).isZero();
  }

  private double echecs() {
    return metriques.counter(AppartenanceService.METRIQUE_ECHEC_INVALIDATION).count();
  }

  @Test
  void siRedisEstInjoignableAvantLeCommitLeChangementEstAnnule() {
    doThrow(new DataAccessResourceFailureException("Redis injoignable"))
        .when(cache)
        .nouvelleGeneration(utilisateur);
    attribuerDansUneEcole();

    // L'exception remonte de beforeCommit : le gestionnaire de transactions fait un rollback.
    assertThatThrownBy(() -> TransactionSynchronizationUtils.triggerBeforeCommit(false))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }

  @Test
  void unEchecApresLeCommitEstCompteSansExceptionNiAnnulation() {
    attribuerDansUneEcole();
    TransactionSynchronizationUtils.triggerBeforeCommit(false);
    doThrow(new DataAccessResourceFailureException("Redis injoignable"))
        .when(cache)
        .nouvelleGeneration(utilisateur);

    assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit).doesNotThrowAnyException();
    verify(cache, times(2)).nouvelleGeneration(utilisateur);
    assertThat(echecs()).isEqualTo(1.0);
  }
}
