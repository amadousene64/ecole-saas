package sn.ecole.etablissement.domain;

import java.util.regex.Pattern;

/** Numéros de téléphone au format E.164, avec +221 par défaut pour un numéro national. */
public final class Telephone {

  private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{7,14}");

  private Telephone() {}

  /**
   * Normalise la saisie : espaces, points, tirets et parenthèses sont ignorés ; « 00 » devient « +
   * » ; un numéro de 9 chiffres est sénégalais (+221). Vide devient {@code null}.
   */
  public static String normaliser(String saisie) {
    if (saisie == null || saisie.isBlank()) {
      return null;
    }
    String numero = saisie.replaceAll("[\\s.\\-()]", "");
    if (numero.startsWith("00")) {
      numero = "+" + numero.substring(2);
    } else if (numero.matches("[0-9]{9}")) {
      numero = "+221" + numero;
    }
    if (!E164.matcher(numero).matches()) {
      throw Verif.invalide(
          "TELEPHONE_INVALIDE", "Numéro de téléphone invalide (format international attendu).");
    }
    return numero;
  }
}
