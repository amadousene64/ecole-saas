package sn.ecole.shared.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sn.ecole.shared.domain.CacheRoles;

/**
 * Invalidation des droits mis en cache pour un utilisateur (rôles, périmètre), au commit de la
 * transaction qui les change. Nouvelle génération juste avant le commit (si Redis est injoignable,
 * l'exception annule la transaction : pas de changement de droits sans invalidation), puis juste
 * après (une lecture concurrente qui aurait mis en cache l'état d'avant le commit devient périmée).
 */
@Component
public class InvalidationDroits {

  private static final Logger LOG = LoggerFactory.getLogger(InvalidationDroits.class);

  /** Métrique : nombre d'échecs de la seconde invalidation (après commit). */
  public static final String METRIQUE_ECHEC_INVALIDATION = "ecole.droits.invalidation.echecs";

  /** Événement d'outbox : invalidation à (re)faire pour un utilisateur, jusqu'à réussite. */
  public static final String A_INVALIDER = "DROITS_A_INVALIDER";

  private final CacheRoles cache;
  private final Counter echecsSecondeInvalidation;
  private final OutboxPublisher outbox;

  /** Pour AppartenanceService (invalidation stricte seulement, sans outbox). */
  public InvalidationDroits(CacheRoles cache, MeterRegistry metriques) {
    this(cache, metriques, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InvalidationDroits(CacheRoles cache, MeterRegistry metriques, OutboxPublisher outbox) {
    this.cache = cache;
    this.outbox = outbox;
    this.echecsSecondeInvalidation =
        Counter.builder(METRIQUE_ECHEC_INVALIDATION)
            .description("Échecs de l'invalidation du cache des droits après commit")
            .register(metriques);
  }

  /**
   * Invalidation qui ne dépend jamais de Redis (retrait d'un accès) : la base reste la référence.
   * Un événement d'outbox est écrit dans la transaction ; après le commit, l'invalidation est
   * tentée tout de suite, et en cas d'échec (Redis arrêté) elle est journalisée puis réessayée par
   * le relais de l'outbox jusqu'à réussite. Pendant la panne, les lectures passent par la base.
   */
  public void auCommitSansRedis(UUID utilisateurId) {
    outbox.publier("Utilisateur", utilisateurId, A_INVALIDER, java.util.Map.of());
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              cache.nouvelleGeneration(utilisateurId);
            } catch (RuntimeException e) {
              echecsSecondeInvalidation.increment();
              LOG.warn(
                  "Invalidation des droits impossible après commit ({}) : réessayée par l'outbox",
                  e.getClass().getSimpleName());
            }
          }
        });
  }

  /**
   * À appeler dans la transaction qui modifie les droits de {@code utilisateurId} : stricte, la
   * transaction échoue si Redis est injoignable (rôles).
   */
  public void auCommit(UUID utilisateurId) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void beforeCommit(boolean lectureSeule) {
            cache.nouvelleGeneration(utilisateurId);
          }

          @Override
          public void afterCommit() {
            try {
              cache.nouvelleGeneration(utilisateurId);
            } catch (RuntimeException e) {
              // Pas de reprise (choix assumé) : signalé par journal et par métrique.
              echecsSecondeInvalidation.increment();
              LOG.warn(
                  "Seconde invalidation des droits impossible : une lecture concurrente au commit"
                      + " peut rester en cache jusqu'à expiration ({})",
                  e.toString());
            }
          }
        });
  }
}
