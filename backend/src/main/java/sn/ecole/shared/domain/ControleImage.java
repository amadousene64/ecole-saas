package sn.ecole.shared.domain;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Contrôle d'une image téléversée (logo, cachet, signature, photo). Le type vient du contenu,
 * jamais de l'en-tête HTTP ni du nom du fichier ; les dimensions sont lues dans l'en-tête et
 * refusées AVANT tout décodage (bombe de décompression) ; l'image est ensuite décodée puis
 * ré-encodée, ce qui supprime les métadonnées (EXIF, GPS) et tout contenu ajouté après les données
 * d'image.
 */
public final class ControleImage {

  /** Côté maximal, en pixels. */
  public static final int DIMENSION_MAX = 2000;

  /**
   * Pour {@link #reduire} : côté maximal de l'image source (photo de téléphone) et nombre maximal
   * de pixels annoncés, contrôlés dans l'en-tête avant tout décodage.
   */
  public static final int SOURCE_COTE_MAX = 12_000;

  public static final long SOURCE_PIXELS_MAX = 60_000_000L;

  private static final byte[] SIGNATURE_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

  static {
    System.setProperty("java.awt.headless", "true");
    ImageIO.setUseCache(false);
  }

  /** Image prête à être stockée. */
  public record ImageValidee(byte[] octets, String contentType, int largeur, int hauteur) {}

  private ControleImage() {}

  /**
   * @param tailleMax taille maximale du fichier, en octets
   */
  public static ImageValidee controler(byte[] brut, int tailleMax) {
    return lire(brut, tailleMax, DIMENSION_MAX, DIMENSION_MAX);
  }

  /**
   * Comme {@link #controler}, mais une image plus grande que {@code coteMax} (jusqu'à {@link
   * #SOURCE_COTE_MAX} de côté) est réduite au décodage (sous-échantillonnage : la pleine résolution
   * n'est jamais en mémoire) au lieu d'être refusée. Pièces du dossier photographiées au téléphone.
   */
  public static ImageValidee reduire(byte[] brut, int tailleMax, int coteMax) {
    return lire(brut, tailleMax, SOURCE_COTE_MAX, coteMax);
  }

  private static ImageValidee lire(byte[] brut, int tailleMax, int sourceMax, int coteMax) {
    if (brut == null || brut.length == 0) {
      throw refus("FICHIER_INVALIDE", "Le fichier est vide.");
    }
    if (brut.length > tailleMax) {
      throw refus(
          "FICHIER_TROP_VOLUMINEUX",
          "Fichier trop volumineux (" + tailleMax / 1000 + " Ko au plus).");
    }
    boolean png = commencePar(brut, SIGNATURE_PNG);
    boolean jpeg =
        !png
            && brut.length > 3
            && (brut[0] & 0xFF) == 0xFF
            && (brut[1] & 0xFF) == 0xD8
            && (brut[2] & 0xFF) == 0xFF;
    if (!png && !jpeg) {
      throw refus("FICHIER_INVALIDE", "Seuls les formats PNG et JPEG sont acceptés.");
    }
    String format = png ? "png" : "jpeg";
    try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(brut))) {
      Iterator<ImageReader> lecteurs = ImageIO.getImageReaders(flux);
      if (!lecteurs.hasNext()) {
        throw refus("FICHIER_INVALIDE", "Image illisible.");
      }
      ImageReader lecteur = lecteurs.next();
      try {
        if (!lecteur.getFormatName().equalsIgnoreCase(format)) {
          throw refus("FICHIER_INVALIDE", "Le contenu ne correspond pas à un PNG ou un JPEG.");
        }
        lecteur.setInput(flux, true, true);
        // En-tête seulement : aucune donnée de pixels n'est décodée à ce stade.
        int largeur = lecteur.getWidth(0);
        int hauteur = lecteur.getHeight(0);
        if (largeur < 1
            || hauteur < 1
            || largeur > sourceMax
            || hauteur > sourceMax
            || (long) largeur * hauteur > SOURCE_PIXELS_MAX) {
          throw refus(
              "IMAGE_TROP_GRANDE",
              "Dimensions refusées : " + sourceMax + " pixels de côté au plus.");
        }
        int pas = Math.ceilDiv(Math.max(largeur, hauteur), coteMax);
        ImageReadParam parametres = lecteur.getDefaultReadParam();
        parametres.setSourceSubsampling(pas, pas, 0, 0);
        BufferedImage image = lecteur.read(0, parametres);
        return new ImageValidee(
            reencoder(image, png),
            png ? "image/png" : "image/jpeg",
            image.getWidth(),
            image.getHeight());
      } finally {
        lecteur.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof ErreurMetier erreur) {
        throw erreur;
      }
      throw refus("FICHIER_INVALIDE", "Image illisible ou corrompue.");
    }
  }

  private static byte[] reencoder(BufferedImage image, boolean png) throws IOException {
    BufferedImage propre = image;
    if (!png) {
      // JPEG : pas de transparence, on repasse en RVB.
      propre = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = propre.createGraphics();
      try {
        g.drawImage(image, 0, 0, null);
      } finally {
        g.dispose();
      }
    }
    ByteArrayOutputStream sortie = new ByteArrayOutputStream();
    if (!ImageIO.write(propre, png ? "png" : "jpeg", sortie)) {
      throw new IOException("Aucun encodeur disponible.");
    }
    return sortie.toByteArray();
  }

  private static boolean commencePar(byte[] octets, byte[] debut) {
    if (octets.length < debut.length) {
      return false;
    }
    for (int i = 0; i < debut.length; i++) {
      if (octets[i] != debut[i]) {
        return false;
      }
    }
    return true;
  }

  private static ErreurMetier refus(String code, String message) {
    return new ErreurMetier(code, TypeErreur.INVALIDE, message);
  }
}
