package sn.ecole.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sn.ecole.shared.application.ChiffrementDonnees;
import sn.ecole.shared.application.EmpreinteSecrets;

/** Chiffrement AES-GCM des données sensibles, HMAC des codes et contrôle des clés au démarrage. */
class ChiffrementEtClesTest {

  private static byte[] cle() {
    byte[] cle = new byte[32];
    new SecureRandom().nextBytes(cle);
    return cle;
  }

  private static String b64(byte[] cle) {
    return Base64.getEncoder().encodeToString(cle);
  }

  private final byte[] k1 = cle();
  private final ChiffrementDonnees aes = new ChiffrementAesGcm("k1", k1, Map.of());

  @Test
  void unChiffreSeRelitDansSonContexteEtNeContientPasLeClair() {
    byte[] chiffre = aes.chiffrer("Asthme, allergie aux arachides", "ecole|eleve|sante");
    assertThat(new String(chiffre, StandardCharsets.ISO_8859_1)).doesNotContain("Asthme");
    assertThat(aes.dechiffrer(chiffre, "ecole|eleve|sante"))
        .isEqualTo("Asthme, allergie aux arachides");
  }

  @Test
  void deuxChiffresDuMemeTexteDifferent() {
    assertThat(aes.chiffrer("x", "c")).isNotEqualTo(aes.chiffrer("x", "c"));
  }

  @Test
  void nullResteNull() {
    assertThat(aes.chiffrer(null, "c")).isNull();
    assertThat(aes.dechiffrer(null, "c")).isNull();
  }

  @Test
  void unChiffreDeplaceVersUnAutreContexteEstRefuse() {
    byte[] chiffre = aes.chiffrer("secret", "ecoleA|eleve1|sante");
    assertThatThrownBy(() -> aes.dechiffrer(chiffre, "ecoleA|eleve2|sante"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> aes.dechiffrer(chiffre, "ecoleB|eleve1|sante"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> aes.dechiffrer(chiffre, "ecoleA|eleve1|allergies"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unChiffreAltereEstRefuse() {
    byte[] chiffre = aes.chiffrer("secret", "c");
    chiffre[chiffre.length - 1] ^= 1;
    assertThatThrownBy(() -> aes.dechiffrer(chiffre, "c"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> aes.dechiffrer(new byte[] {9, 9}, "c"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void apresRotationLesAnciensChiffresRestentLisibles() {
    byte[] ancien = aes.chiffrer("avant rotation", "c");
    ChiffrementDonnees apres = new ChiffrementAesGcm("k2", cle(), Map.of("k1", k1));
    assertThat(apres.dechiffrer(ancien, "c")).isEqualTo("avant rotation");
    byte[] nouveau = apres.chiffrer("après", "c");
    assertThatThrownBy(() -> aes.dechiffrer(nouveau, "c"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("inconnue");
  }

  @Test
  void lEmpreinteDependDuSecretDuContexteEtDeLaCle() {
    EmpreinteSecrets hmac = new EmpreinteHmac(cle());
    byte[] e = hmac.empreinte("ABCD2345", "ecoleA|K7QP");
    assertThat(hmac.correspond("ABCD2345", "ecoleA|K7QP", e)).isTrue();
    assertThat(hmac.correspond("ABCD2346", "ecoleA|K7QP", e)).isFalse();
    assertThat(hmac.correspond("ABCD2345", "ecoleB|K7QP", e)).isFalse();
    assertThat(new EmpreinteHmac(cle()).correspond("ABCD2345", "ecoleA|K7QP", e)).isFalse();
    assertThat(hmac.correspond("ABCD2345", "ecoleA|K7QP", null)).isFalse();
  }

  @Test
  void sansCleOuAvecUneCleInvalideLApplicationNeDemarrePas() {
    ClesConfig config = new ClesConfig();
    String bonne = b64(cle());
    assertThatThrownBy(() -> config.chiffrementDonnees(new ClesProperties(null, null, null, bonne)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("absente");
    assertThatThrownBy(() -> config.chiffrementDonnees(new ClesProperties(bonne, null, null, "  ")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                config.chiffrementDonnees(new ClesProperties("pas-du-base64!", null, null, bonne)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Base64");
    assertThatThrownBy(
            () ->
                config.chiffrementDonnees(new ClesProperties(b64(new byte[16]), null, null, bonne)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 octets");
  }

  @Test
  void lesClesDeChiffrementEtHmacDoiventEtreDistinctes() {
    String meme = b64(cle());
    assertThatThrownBy(
            () -> new ClesConfig().chiffrementDonnees(new ClesProperties(meme, null, null, meme)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("distinctes");
    String autre = b64(cle());
    assertThatThrownBy(
            () ->
                new ClesConfig()
                    .chiffrementDonnees(new ClesProperties(autre, "k2", Map.of("k1", meme), meme)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("distinctes");
  }
}
