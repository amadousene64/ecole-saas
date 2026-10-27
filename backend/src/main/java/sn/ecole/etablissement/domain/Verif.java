package sn.ecole.etablissement.domain;

import java.util.regex.Pattern;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/** Contrôles de saisie communs aux entités du module. */
final class Verif {

  private static final Pattern CODE = Pattern.compile("[A-Z0-9]{1,10}");

  private Verif() {}

  static ErreurMetier invalide(String code, String message) {
    return new ErreurMetier(code, TypeErreur.INVALIDE, message);
  }

  static ErreurMetier regle(String code, String message) {
    return new ErreurMetier(code, TypeErreur.REGLE_METIER, message);
  }

  static ErreurMetier conflit(String code, String message) {
    return new ErreurMetier(code, TypeErreur.CONFLIT, message);
  }

  /** Texte obligatoire : rogné, non vide, au plus {@code max} caractères. */
  static String exiger(String valeur, String champ, int max) {
    String propre = valeur == null ? "" : valeur.strip();
    if (propre.isEmpty() || propre.length() > max) {
      throw invalide(
          "CHAMP_INVALIDE", champ + " est obligatoire (" + max + " caractères au plus).");
    }
    return propre;
  }

  /** Texte facultatif : vide devient {@code null}. */
  static String optionnel(String valeur, String champ, int max) {
    String propre = valeur == null ? "" : valeur.strip();
    if (propre.isEmpty()) {
      return null;
    }
    if (propre.length() > max) {
      throw invalide("CHAMP_INVALIDE", champ + " dépasse " + max + " caractères.");
    }
    return propre;
  }

  /** Code court en majuscules (niveau, série) : lettres et chiffres, 10 au plus. */
  static String code(String valeur, String champ) {
    String propre = exiger(valeur, champ, 10).toUpperCase(java.util.Locale.ROOT);
    if (!CODE.matcher(propre).matches()) {
      throw invalide("CHAMP_INVALIDE", champ + " ne contient que des lettres et des chiffres.");
    }
    return propre;
  }
}
