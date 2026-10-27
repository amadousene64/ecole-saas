package sn.ecole.etablissement.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Actions sur le stockage d'objets à déclencher selon l'issue de la transaction : MinIO n'est pas
 * transactionnel, on ne supprime l'ancien fichier qu'une fois la base validée, et on retire le
 * nouveau si la base est annulée.
 */
final class ApresTransaction {

  private static final Logger LOG = LoggerFactory.getLogger(ApresTransaction.class);

  private ApresTransaction() {}

  static void siValidee(Runnable action) {
    enregistrer(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            executer(action);
          }
        });
  }

  static void siAnnulee(Runnable action) {
    enregistrer(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int statut) {
            if (statut != STATUS_COMMITTED) {
              executer(action);
            }
          }
        });
  }

  private static void enregistrer(TransactionSynchronization synchronisation) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("Aucune transaction active.");
    }
    TransactionSynchronizationManager.registerSynchronization(synchronisation);
  }

  /**
   * Un échec de nettoyage ne doit pas masquer le résultat : il laisse au pire un objet orphelin.
   */
  private static void executer(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      LOG.warn("Nettoyage du stockage en échec (objet orphelin possible)", e);
    }
  }
}
