package sn.ecole.inscription.domain;

import java.util.Set;

/** Cycle de vie d'une inscription : PRE_INSCRIT puis INSCRIT, puis éventuellement une sortie. */
public enum StatutInscription {
  PRE_INSCRIT,
  INSCRIT,
  TRANSFERE,
  RADIE,
  ABANDON;

  /** Statuts qui occupent une place dans la classe (une seule inscription active par année). */
  public static final Set<StatutInscription> ACTIFS = Set.of(PRE_INSCRIT, INSCRIT);

  /** Statuts de sortie. */
  public static final Set<StatutInscription> SORTIES = Set.of(TRANSFERE, RADIE, ABANDON);

  public boolean actif() {
    return ACTIFS.contains(this);
  }
}
