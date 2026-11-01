package sn.ecole.inscription.domain;

/** Compteur atomique des numéros d'ordre des matricules, par école et par clé. */
public interface CompteurMatricule {

  /** Numéro suivant (1 au premier appel) ; atomique sous concurrence. */
  long suivant(String cle);
}
