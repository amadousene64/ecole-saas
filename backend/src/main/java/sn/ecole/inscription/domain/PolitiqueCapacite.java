package sn.ecole.inscription.domain;

/** Que faire quand une inscription dépasse la capacité de la classe. */
public enum PolitiqueCapacite {
  /** L'inscription est enregistrée, avec un avertissement. */
  AVERTIR,
  /** L'inscription est refusée. */
  BLOQUER
}
