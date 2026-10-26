package sn.ecole.shared.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

/**
 * Seule porte d'accès à la base hors requête HTTP (relais outbox, tâches planifiées). Chaque
 * exécution ouvre une nouvelle transaction dans laquelle {@code app.tenant_id} et le filtre
 * Hibernate valent l'école donnée ; sans elle, une requête ne voit aucune ligne.
 */
@Component
public class ExecutionPourTenant {

  private static final Logger LOG = LoggerFactory.getLogger(ExecutionPourTenant.class);

  private final TransactionTemplate ecriture;
  private final TransactionTemplate lecture;
  private final TenantRepository tenants;

  public ExecutionPourTenant(PlatformTransactionManager transactions, TenantRepository tenants) {
    this.ecriture = new TransactionTemplate(transactions);
    this.ecriture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.lecture = new TransactionTemplate(transactions);
    this.lecture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.lecture.setReadOnly(true);
    this.tenants = tenants;
  }

  /** Exécute {@code action} dans une nouvelle transaction pour l'école {@code tenantId}. */
  public <T> T executer(UUID tenantId, Supplier<T> action) {
    return dans(tenantId, ecriture, action);
  }

  public void executer(UUID tenantId, Runnable action) {
    dans(
        tenantId,
        ecriture,
        () -> {
          action.run();
          return null;
        });
  }

  /** Variante en lecture seule. */
  public <T> T lire(UUID tenantId, Supplier<T> action) {
    return dans(tenantId, lecture, action);
  }

  /**
   * Exécute {@code action} pour chaque école active, chacune dans sa propre transaction. L'échec
   * d'une école est journalisé et rapporté dans le bilan ; il n'empêche pas les suivantes.
   */
  public BilanExecution pourChaqueEcole(String tache, Consumer<UUID> action) {
    if (ContexteTenant.courant().isPresent()) {
      throw new IllegalStateException(
          "pourChaqueEcole ne s'appelle pas depuis un contexte d'école (requête HTTP).");
    }
    List<UUID> ecoles =
        lecture.execute(
            statut -> tenants.findByActifTrueOrderByCode().stream().map(Tenant::getId).toList());
    List<UUID> reussies = new ArrayList<>();
    List<UUID> echouees = new ArrayList<>();
    for (UUID ecole : Objects.requireNonNull(ecoles)) {
      try {
        executer(ecole, () -> action.accept(ecole));
        reussies.add(ecole);
      } catch (RuntimeException e) {
        LOG.error("Tâche « {} » en échec pour l'école {}", tache, ecole, e);
        echouees.add(ecole);
      }
    }
    return new BilanExecution(tache, reussies, echouees);
  }

  private <T> T dans(UUID tenantId, TransactionTemplate modele, Supplier<T> action) {
    Objects.requireNonNull(tenantId, "tenantId");
    ContexteTenant.courant()
        .filter(courant -> !courant.equals(tenantId))
        .ifPresent(
            courant -> {
              throw new IllegalStateException(
                  "Changement d'école interdit dans un contexte ouvert pour une autre école.");
            });
    return ContexteTenant.executer(tenantId, () -> modele.execute(statut -> action.get()));
  }
}
