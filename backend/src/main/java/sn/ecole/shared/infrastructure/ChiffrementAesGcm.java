package sn.ecole.shared.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import sn.ecole.shared.application.ChiffrementDonnees;

/**
 * AES-256-GCM, nonce aléatoire de 96 bits par valeur, contexte en données authentifiées (AAD).
 *
 * <p>Format : {@code 0x01 | longueur de l'id de clé | id de clé | nonce (12) | chiffré + tag (16)}.
 * L'identifiant de clé permet la rotation : on chiffre avec la clé active, on relit avec n'importe
 * quelle clé connue.
 */
final class ChiffrementAesGcm implements ChiffrementDonnees {

  private static final byte FORMAT = 1;
  private static final int NONCE = 12;
  private static final int TAG_BITS = 128;

  private final String idActif;
  private final Map<String, SecretKey> cles;
  private final SecureRandom aleatoire = new SecureRandom();

  ChiffrementAesGcm(String idActif, byte[] cleActive, Map<String, byte[]> anciennes) {
    if (idActif.length() > 20 || !idActif.matches("[A-Za-z0-9_-]+")) {
      throw new IllegalStateException("Identifiant de clé invalide.");
    }
    this.idActif = idActif;
    Map<String, SecretKey> toutes = new HashMap<>();
    anciennes.forEach((id, cle) -> toutes.put(id, new SecretKeySpec(cle, "AES")));
    toutes.put(idActif, new SecretKeySpec(cleActive, "AES"));
    this.cles = Map.copyOf(toutes);
  }

  @Override
  public byte[] chiffrer(String clair, String contexte) {
    if (clair == null) {
      return null;
    }
    try {
      byte[] nonce = new byte[NONCE];
      aleatoire.nextBytes(nonce);
      Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
      aes.init(Cipher.ENCRYPT_MODE, cles.get(idActif), new GCMParameterSpec(TAG_BITS, nonce));
      aes.updateAAD(contexte.getBytes(StandardCharsets.UTF_8));
      byte[] chiffre = aes.doFinal(clair.getBytes(StandardCharsets.UTF_8));
      byte[] id = idActif.getBytes(StandardCharsets.US_ASCII);
      return ByteBuffer.allocate(2 + id.length + NONCE + chiffre.length)
          .put(FORMAT)
          .put((byte) id.length)
          .put(id)
          .put(nonce)
          .put(chiffre)
          .array();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Chiffrement impossible.", e);
    }
  }

  @Override
  public String dechiffrer(byte[] valeur, String contexte) {
    if (valeur == null) {
      return null;
    }
    try {
      ByteBuffer tampon = ByteBuffer.wrap(valeur);
      if (tampon.get() != FORMAT) {
        throw new IllegalStateException("Format de chiffré inconnu.");
      }
      byte[] id = new byte[tampon.get()];
      tampon.get(id);
      SecretKey cle = cles.get(new String(id, StandardCharsets.US_ASCII));
      if (cle == null) {
        throw new IllegalStateException("Clé de chiffrement inconnue.");
      }
      byte[] nonce = new byte[NONCE];
      tampon.get(nonce);
      byte[] chiffre = new byte[tampon.remaining()];
      tampon.get(chiffre);
      Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
      aes.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(TAG_BITS, nonce));
      aes.updateAAD(contexte.getBytes(StandardCharsets.UTF_8));
      return new String(aes.doFinal(chiffre), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | RuntimeException e) {
      if (e instanceof IllegalStateException etat) {
        throw etat;
      }
      // Tag invalide : valeur altérée, déplacée vers une autre ligne, ou mauvaise clé.
      throw new IllegalStateException("Déchiffrement impossible.", e);
    }
  }
}
