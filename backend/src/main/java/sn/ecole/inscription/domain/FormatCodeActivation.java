package sn.ecole.inscription.domain;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

/**
 * Codes d'activation lisibles : 12 caractères de l'alphabet de Crockford (sans I, L, O, U),
 * présentés XXXX-XXXX-XXXX. Les 4 premiers forment l'identifiant (recherche du code), les 8
 * suivants le secret (40 bits, jamais stocké en clair). La saisie tolère minuscules, espaces,
 * tirets et confusions courantes (O pour 0, I ou L pour 1).
 */
public final class FormatCodeActivation {

  public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  static final int LONGUEUR_IDENTIFIANT = 4;
  static final int LONGUEUR_SECRET = 8;

  private static final SecureRandom ALEATOIRE = new SecureRandom();

  /** Code tiré au hasard : identifiant et secret. */
  public record CodeTire(String identifiant, String secret) {

    /** Présentation remise au parent : XXXX-XXXX-XXXX. */
    public String presentation() {
      return identifiant + "-" + secret.substring(0, 4) + "-" + secret.substring(4);
    }

    @Override
    public String toString() {
      return "CodeTire[" + identifiant + "-****-****]";
    }
  }

  private FormatCodeActivation() {}

  public static CodeTire tirer() {
    return new CodeTire(tirer(LONGUEUR_IDENTIFIANT), tirer(LONGUEUR_SECRET));
  }

  private static String tirer(int longueur) {
    StringBuilder sb = new StringBuilder(longueur);
    for (int i = 0; i < longueur; i++) {
      sb.append(ALPHABET.charAt(ALEATOIRE.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  /** Code saisi normalisé et découpé ; vide s'il n'a pas la forme d'un code. */
  public static Optional<CodeTire> lire(String saisie) {
    if (saisie == null || saisie.length() > 40) {
      return Optional.empty();
    }
    StringBuilder sb = new StringBuilder();
    for (char c : saisie.toUpperCase(Locale.ROOT).toCharArray()) {
      if (c == ' ' || c == '-') {
        continue;
      }
      char corrige =
          switch (c) {
            case 'O' -> '0';
            case 'I', 'L' -> '1';
            default -> c;
          };
      if (ALPHABET.indexOf(corrige) < 0) {
        return Optional.empty();
      }
      sb.append(corrige);
    }
    if (sb.length() != LONGUEUR_IDENTIFIANT + LONGUEUR_SECRET) {
      return Optional.empty();
    }
    return Optional.of(
        new CodeTire(sb.substring(0, LONGUEUR_IDENTIFIANT), sb.substring(LONGUEUR_IDENTIFIANT)));
  }
}
