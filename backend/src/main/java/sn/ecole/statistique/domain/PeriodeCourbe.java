package sn.ecole.statistique.domain;

/** Fenêtre de la courbe des inscriptions. */
public enum PeriodeCourbe {
  /** Les 30 derniers jours. */
  JOURS_30,
  /** Les 90 derniers jours. */
  JOURS_90,
  /** Depuis trois mois avant le début de l'année (les inscriptions précèdent la rentrée). */
  ANNEE
}
