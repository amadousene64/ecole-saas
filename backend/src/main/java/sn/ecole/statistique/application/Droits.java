package sn.ecole.statistique.application;

/** Expressions d'autorisation du module : les statistiques sont réservées à la direction. */
final class Droits {

  static final String DIRECTION = "hasRole('DIRECTEUR')";

  private Droits() {}
}
