package sn.ecole.shared.application;

import sn.ecole.shared.domain.OutboxEvent;

/**
 * Traite un type d'événement de l'outbox (analyse antivirus, SMS, PDF…). Appelé par le {@link
 * RelaisOutbox} dans une transaction ouverte pour l'école de l'événement : les écritures en base du
 * traitement sont validées avec le marquage « traité », ou annulées avec lui.
 *
 * <p>Un traitement doit être idempotent : après un arrêt brutal, un événement peut être rejoué.
 */
public interface TraitementOutbox {

  /** Valeur de {@code type_evenement} prise en charge. */
  String typeEvenement();

  /** Lève une exception pour demander une nouvelle tentative plus tard. */
  void traiter(OutboxEvent evenement);
}
