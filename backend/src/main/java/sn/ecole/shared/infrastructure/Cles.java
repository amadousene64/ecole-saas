package sn.ecole.shared.infrastructure;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * Lecture et contrôle des clés : absentes, mal formées ou trop courtes, l'application refuse de
 * démarrer.
 */
final class Cles {

  static final int TAILLE = 32;

  private Cles() {}

  static byte[] decoder(String base64, String nom) {
    if (base64 == null || base64.isBlank()) {
      throw new IllegalStateException(
          "Clé " + nom + " absente : à fournir par l'environnement (jamais dans le dépôt).");
    }
    byte[] cle;
    try {
      cle = Base64.getDecoder().decode(base64.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Clé " + nom + " : Base64 invalide.", e);
    }
    if (cle.length != TAILLE) {
      throw new IllegalStateException("Clé " + nom + " : " + TAILLE + " octets attendus.");
    }
    return cle;
  }

  static boolean identiques(byte[] a, byte[] b) {
    return MessageDigest.isEqual(a, b);
  }
}
