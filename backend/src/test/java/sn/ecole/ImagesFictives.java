package sn.ecole;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Images de test avec métadonnées fictives (EXIF, position GPS, texte PNG). */
public final class ImagesFictives {

  private ImagesFictives() {}

  /** JPEG dont le segment APP1 EXIF contient une position GPS fictive (14°41'24" N). */
  public static byte[] jpegAvecGps(int largeur, int hauteur) {
    byte[] jpeg = encoder(dessin(largeur, hauteur, BufferedImage.TYPE_INT_RGB), "jpeg");
    byte[] exif = exifAvecGps();
    ByteArrayOutputStream app1 = new ByteArrayOutputStream();
    app1.write(0xFF);
    app1.write(0xE1);
    int longueur = 2 + exif.length;
    app1.write(longueur >> 8);
    app1.write(longueur & 0xFF);
    app1.writeBytes(exif);
    // Inséré juste après le segment APP0 (JFIF) écrit par ImageIO.
    int finApp0 = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
    ByteArrayOutputStream sortie = new ByteArrayOutputStream();
    sortie.write(jpeg, 0, finApp0);
    sortie.writeBytes(app1.toByteArray());
    sortie.write(jpeg, finApp0, jpeg.length - finApp0);
    return sortie.toByteArray();
  }

  /** PNG avec un bloc tEXt (commentaire) inséré après l'en-tête. */
  public static byte[] pngAvecTexte(String texte) {
    byte[] png = encoder(dessin(300, 200, BufferedImage.TYPE_INT_ARGB), "png");
    byte[] donnees = ("Comment\0" + texte).getBytes(StandardCharsets.ISO_8859_1);
    ByteArrayOutputStream bloc = new ByteArrayOutputStream();
    bloc.writeBytes(ByteBuffer.allocate(4).putInt(donnees.length).array());
    byte[] nom = "tEXt".getBytes(StandardCharsets.US_ASCII);
    bloc.writeBytes(nom);
    bloc.writeBytes(donnees);
    CRC32 crc = new CRC32();
    crc.update(nom);
    crc.update(donnees);
    bloc.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    int finIhdr = 8 + 8 + 13 + 4;
    ByteArrayOutputStream sortie = new ByteArrayOutputStream();
    sortie.write(png, 0, finIhdr);
    sortie.writeBytes(bloc.toByteArray());
    sortie.write(png, finIhdr, png.length - finIhdr);
    return sortie.toByteArray();
  }

  /** Nombre de segments APP1 (EXIF, XMP) avant les données d'image d'un JPEG. */
  public static int segmentsApp1(byte[] jpeg) {
    int n = 0;
    int i = 2;
    while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
      int marqueur = jpeg[i + 1] & 0xFF;
      if (marqueur == 0xDA) {
        break;
      }
      if (marqueur == 0xE1) {
        n++;
      }
      i += 2 + (((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF));
    }
    return n;
  }

  public static boolean contient(byte[] octets, String ascii) {
    return new String(octets, StandardCharsets.ISO_8859_1).contains(ascii);
  }

  private static BufferedImage dessin(int largeur, int hauteur, int type) {
    BufferedImage image = new BufferedImage(largeur, hauteur, type);
    Graphics2D g = image.createGraphics();
    try {
      g.setPaint(new GradientPaint(0, 0, Color.WHITE, largeur, hauteur, new Color(30, 90, 60)));
      g.fillRect(0, 0, largeur, hauteur);
    } finally {
      g.dispose();
    }
    return image;
  }

  private static byte[] encoder(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream sortie = new ByteArrayOutputStream();
      ImageIO.write(image, format, sortie);
      return sortie.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** « Exif\0\0 » + TIFF petit-boutiste : IFD0 → GPSInfo → latitude et sa référence. */
  private static byte[] exifAvecGps() {
    ByteBuffer tiff = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN);
    tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
    // IFD0 (offset 8) : une entrée, GPSInfo (0x8825) → IFD GPS à l'offset 26.
    tiff.putShort((short) 1);
    tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(26);
    tiff.putInt(0);
    // IFD GPS (offset 26) : GPSLatitudeRef « N », GPSLatitude (3 rationnels à l'offset 56).
    tiff.putShort((short) 2);
    tiff.putShort((short) 1).putShort((short) 2).putInt(2).put((byte) 'N').put(new byte[3]);
    tiff.putShort((short) 2).putShort((short) 5).putInt(3).putInt(56);
    tiff.putInt(0);
    tiff.putInt(14).putInt(1).putInt(41).putInt(1).putInt(2400).putInt(100);
    ByteArrayOutputStream exif = new ByteArrayOutputStream();
    exif.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
    exif.write(tiff.array(), 0, tiff.position());
    // Marqueur lisible pour les assertions (« GPS ») en fin de segment, ignoré par les lecteurs.
    exif.writeBytes("GPS-FICTIF".getBytes(StandardCharsets.US_ASCII));
    return exif.toByteArray();
  }
}
