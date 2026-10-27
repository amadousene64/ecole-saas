package sn.ecole.etablissement.domain;

/** Cycle de vie d'une année scolaire : PREPARATION puis EN_COURS puis CLOTUREE (définitif). */
public enum StatutAnnee {
  PREPARATION,
  EN_COURS,
  CLOTUREE
}
