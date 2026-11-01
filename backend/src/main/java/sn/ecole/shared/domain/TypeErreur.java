package sn.ecole.shared.domain;

/** Nature d'une erreur métier ; la couche api la traduit en statut HTTP. */
public enum TypeErreur {
  INVALIDE,
  NON_AUTHENTIFIE,
  INTERDIT,
  INTROUVABLE,
  CONFLIT,
  REGLE_METIER,
  /** Trop de tentatives (limitation de débit) : 429. */
  TROP_DE_TENTATIVES
}
