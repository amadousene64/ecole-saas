package sn.ecole.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ControleImageTest {

  private static byte[] image(String format, int largeur, int hauteur) throws IOException {
    BufferedImage image =
        new BufferedImage(
            largeur,
            hauteur,
            format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream sortie = new ByteArrayOutputStream();
    ImageIO.write(image, format, sortie);
    return sortie.toByteArray();
  }

  /**
   * PNG minimal dont l'en-tête annonce {@code largeur x hauteur}, sans vraies données de pixels.
   */
  private static byte[] pngAnnoncant(int largeur, int hauteur) throws IOException {
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
    ihdr.write(entier(largeur));
    ihdr.write(entier(hauteur));
    ihdr.write(new byte[] {8, 2, 0, 0, 0});
    bloc(png, "IHDR", ihdr.toByteArray());
    Deflater deflater = new Deflater();
    deflater.setInput(new byte[] {0});
    deflater.finish();
    byte[] compresse = new byte[32];
    int n = deflater.deflate(compresse);
    bloc(png, "IDAT", java.util.Arrays.copyOf(compresse, n));
    bloc(png, "IEND", new byte[0]);
    return png.toByteArray();
  }

  private static void bloc(ByteArrayOutputStream sortie, String type, byte[] donnees)
      throws IOException {
    byte[] nom = type.getBytes(StandardCharsets.US_ASCII);
    sortie.write(entier(donnees.length));
    sortie.write(nom);
    sortie.write(donnees);
    CRC32 crc = new CRC32();
    crc.update(nom);
    crc.update(donnees);
    sortie.write(entier((int) crc.getValue()));
  }

  private static byte[] entier(int v) {
    return new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
  }

  private static void assertCode(byte[] brut, int tailleMax, String code) {
    assertThatThrownBy(() -> ControleImage.controler(brut, tailleMax))
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void unPngValideEstAccepteEtReencode() throws IOException {
    var resultat = ControleImage.controler(image("png", 120, 80), 1_000_000);
    assertThat(resultat.contentType()).isEqualTo("image/png");
    assertThat(resultat.largeur()).isEqualTo(120);
    assertThat(resultat.hauteur()).isEqualTo(80);
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(resultat.octets()))).isNotNull();
  }

  @Test
  void unJpegValideEstAccepte() throws IOException {
    var resultat = ControleImage.controler(image("jpeg", 200, 100), 1_000_000);
    assertThat(resultat.contentType()).isEqualTo("image/jpeg");
  }

  @Test
  void lesDimensionsMaximalesSontAcceptees() throws IOException {
    assertThat(ControleImage.controler(image("png", 2000, 10), 1_000_000).largeur())
        .isEqualTo(2000);
  }

  @Test
  void auDelaDe2000PixelsEstRefuse() throws IOException {
    assertCode(image("png", 2001, 10), 1_000_000, "IMAGE_TROP_GRANDE");
    assertCode(image("jpeg", 10, 2001), 1_000_000, "IMAGE_TROP_GRANDE");
  }

  @Test
  void uneBombeDeDecompressionEstRefuseSurSonEnTeteAvantToutDecodage() throws IOException {
    // L'en-tête annonce 30 000 x 30 000 pixels (3,6 Go décodés) dans un fichier de quelques octets.
    byte[] bombe = pngAnnoncant(30_000, 30_000);
    assertThat(bombe.length).isLessThan(200);
    // Le code IMAGE_TROP_GRANDE (et non FICHIER_INVALIDE) prouve que la lecture s'est arrêtée à
    // l'en-tête : un décodage aurait échoué ou épuisé la mémoire.
    assertCode(bombe, 1_000_000, "IMAGE_TROP_GRANDE");
  }

  @Test
  void leSvgEtLesAutresFormatsSontRefuses() {
    assertCode(
        "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
            .getBytes(StandardCharsets.UTF_8),
        1_000_000,
        "FICHIER_INVALIDE");
    assertCode("GIF89a".getBytes(StandardCharsets.US_ASCII), 1_000_000, "FICHIER_INVALIDE");
    assertCode("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 1_000_000, "FICHIER_INVALIDE");
  }

  @Test
  void uneSignaturePngSansImageEstRefusee() {
    byte[] faux = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    assertCode(faux, 1_000_000, "FICHIER_INVALIDE");
  }

  @Test
  void unFichierVideOuTropVolumineuxEstRefuse() throws IOException {
    assertCode(new byte[0], 1_000_000, "FICHIER_INVALIDE");
    byte[] gros = new byte[512_000 + 1];
    assertCode(gros, 512_000, "FICHIER_TROP_VOLUMINEUX");
  }

  @Test
  void lesDonneesAjouteesApresLImageDisparaissentAuReencodage() throws IOException {
    ByteArrayOutputStream piege = new ByteArrayOutputStream();
    piege.write(image("png", 20, 20));
    piege.write("SECRET-PAYLOAD".getBytes(StandardCharsets.US_ASCII));

    var resultat = ControleImage.controler(piege.toByteArray(), 1_000_000);

    assertThat(new String(resultat.octets(), StandardCharsets.ISO_8859_1))
        .doesNotContain("SECRET-PAYLOAD");
  }
}
