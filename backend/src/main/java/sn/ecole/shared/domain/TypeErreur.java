package sn.ecole.shared.domain;

/** Nature d'une erreur métier ; la couche api la traduit en statut HTTP. */
public enum TypeErreur {
  INVALIDE,
  NON_AUTHENTIFIE,
  INTERDIT,
  INTROUVABLE,
  CONFLIT,
  REGLE_METIER
}
