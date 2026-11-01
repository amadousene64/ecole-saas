package sn.ecole.shared.domain;

import java.time.Duration;

/**
 * Compteur de tentatives par fenêtre fixe (ex. : 10 activations par heure et par adresse IP). Fermé
 * en cas de panne : si le compteur est indisponible, la tentative est refusée.
 */
public interface LimiteurDebit {

  /**
   * Compte une tentative pour {@code cle}.
   *
   * @return vrai si la tentative reste sous le plafond {@code max} dans la fenêtre courante
   */
  boolean autoriser(String cle, int max, Duration fenetre);
}
