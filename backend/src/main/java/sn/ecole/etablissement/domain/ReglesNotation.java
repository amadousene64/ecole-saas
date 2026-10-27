package sn.ecole.etablissement.domain;

import sn.ecole.etablissement.ModeNotation;

/** Cohérence des paramètres de notation. */
public final class ReglesNotation {

  private ReglesNotation() {}

  /** La notation par coefficients est sur 20 : la base 10 n'existe qu'avec les barèmes. */
  public static void verifier(ModeNotation mode, int baseMoyenne) {
    if (mode == ModeNotation.COEFFICIENT && baseMoyenne == 10) {
      throw Verif.regle(
          "BASE_INCOMPATIBLE",
          "La base 10 n'est possible qu'en notation par barème : les coefficients sont sur 20.");
    }
  }
}
