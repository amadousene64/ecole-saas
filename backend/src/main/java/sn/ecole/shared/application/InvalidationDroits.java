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

  private final CacheRoles cache;
  private final Counter echecsSecondeInvalidation;

  public InvalidationDroits(CacheRoles cache, MeterRegistry metriques) {
    this.cache = cache;
    this.echecsSecondeInvalidation =
        Counter.builder(METRIQUE_ECHEC_INVALIDATION)
            .description("Échecs de l'invalidation du cache des droits après commit")
            .register(metriques);
  }

  /** À appeler dans la transaction qui modifie les droits de {@code utilisateurId}. */
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
