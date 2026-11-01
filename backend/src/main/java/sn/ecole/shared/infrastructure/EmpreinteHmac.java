package sn.ecole.shared.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import sn.ecole.shared.application.EmpreinteSecrets;

/** HMAC-SHA256 ; le contexte est séparé du secret par un octet nul. */
final class EmpreinteHmac implements EmpreinteSecrets {

  private final SecretKeySpec cle;

  EmpreinteHmac(byte[] cle) {
    this.cle = new SecretKeySpec(cle, "HmacSHA256");
  }

  @Override
  public byte[] empreinte(String secret, String contexte) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(cle);
      mac.update(contexte.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) 0);
      return mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Empreinte impossible.", e);
    }
  }

  @Override
  public boolean correspond(String secret, String contexte, byte[] empreinteAttendue) {
    return empreinteAttendue != null
        && MessageDigest.isEqual(empreinte(secret, contexte), empreinteAttendue);
  }
}
