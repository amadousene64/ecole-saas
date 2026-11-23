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
import sn.ecole.shared.domain.OutboxEventRepository;

/**
 * Invalidation des droits mis en cache pour un utilisateur (rôles, périmètre), au commit de la
 * transaction qui les change, sans jamais dépendre de Redis : la base reste la référence.
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
  private final OutboxEventRepository evenements;

  public InvalidationDroits(
      CacheRoles cache,
      MeterRegistry metriques,
      OutboxPublisher outbox,
      OutboxEventRepository evenements) {
    this.cache = cache;
    this.outbox = outbox;
    this.evenements = evenements;
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
   * Vrai tant qu'une invalidation de l'utilisateur attend d'être rejouée (Redis était injoignable)
   * : ses entrées de cache peuvent être périmées, il faut lire en base. Reste vrai si l'événement a
   * épuisé ses tentatives (lecture en base, jamais de cache périmé).
   */
  public boolean enAttente(UUID utilisateurId) {
    return evenements.existsByTypeEvenementAndIdAgregatAndTraiteLeIsNull(
        A_INVALIDER, utilisateurId);
  }
}
