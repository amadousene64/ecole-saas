package sn.ecole.inscription.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Matricule d'un élève : majuscules, chiffres, « - » et « / », 30 caractères au plus. */
public final class Matricule {

  private static final Pattern FORME = Pattern.compile("[A-Z0-9][A-Z0-9/-]{0,29}");

  private Matricule() {}

  /** Normalise (majuscules, sans espaces) et vérifie la forme d'un matricule. */
  public static String verifier(String valeur) {
    String propre = valeur == null ? "" : valeur.strip().toUpperCase(Locale.ROOT);
    if (!FORME.matcher(propre).matches()) {
      throw Saisies.invalide(
          "MATRICULE_INVALIDE",
          "Le matricule ne contient que des lettres, chiffres, « - » et « / » (30 au plus).");
    }
    return propre;
  }
}
