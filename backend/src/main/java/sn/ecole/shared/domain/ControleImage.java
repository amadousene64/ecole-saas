package sn.ecole.shared.domain;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Contrôle d'une image téléversée (logo, cachet, signature, photo). Le type vient du contenu,
 * jamais de l'en-tête HTTP ni du nom du fichier ; les dimensions sont lues dans l'en-tête et
 * refusées AVANT tout décodage (bombe de décompression) ; l'image est ensuite décodée puis
 * ré-encodée, ce qui supprime les métadonnées (EXIF, GPS) et tout contenu ajouté après les données
 * d'image. L'orientation EXIF d'un JPEG (photo de téléphone prise en portrait) est appliquée aux
 * pixels avant ce ré-encodage, sinon elle serait perdue avec le reste des métadonnées.
 */
public final class ControleImage {

  /** Côté maximal, en pixels. */
  public static final int DIMENSION_MAX = 2000;

  /**
   * Pour {@link #reduire} : côté maximal de l'image source (photo de téléphone) et nombre maximal
   * de pixels annoncés (24 Mpx : un téléphone courant en produit 12), contrôlés dans l'en-tête
   * avant tout décodage. Le décodeur JPEG garde en mémoire native tous les coefficients d'un JPEG
   * progressif, même sous-échantillonné : cette borne limite cette mémoire.
   */
  public static final int SOURCE_COTE_MAX = 8_000;

  public static final long SOURCE_PIXELS_MAX = 24_000_000L;

  /** Décodages simultanés pour toute l'instance : borne la mémoire native et le CPU. */
  private static final Semaphore DECODAGES = new Semaphore(2);

  private static final long ATTENTE_DECODAGE_SECONDES = 10;

  private static final byte[] EN_TETE_EXIF = {'E', 'x', 'i', 'f', 0, 0};

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
    try {
      if (!DECODAGES.tryAcquire(ATTENTE_DECODAGE_SECONDES, TimeUnit.SECONDS)) {
        throw new ErreurMetier(
            "SERVEUR_OCCUPE",
            TypeErreur.TROP_DE_TENTATIVES,
            "Trop d'images en cours de traitement ; réessayez dans un instant.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw refus("FICHIER_INVALIDE", "Traitement de l'image interrompu.");
    }
    try {
      return decoder(brut, png, format, sourceMax, coteMax);
    } finally {
      DECODAGES.release();
    }
  }

  private static ImageValidee decoder(
      byte[] brut, boolean png, String format, int sourceMax, int coteMax) {
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
        if (!png) {
          image = appliquerOrientation(image, orientationExif(brut));
        }
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

  /**
   * Orientation EXIF (1 à 8) d'un JPEG, 1 (aucune) si elle est absente ou illisible. Lecture bornée
   * du premier segment APP1 « Exif » : aucun décalage n'est suivi hors de ce segment.
   */
  static int orientationExif(byte[] jpeg) {
    int i = 2;
    while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
      int marqueur = jpeg[i + 1] & 0xFF;
      if (marqueur == 0xFF) {
        i++;
      } else if (marqueur == 0xDA || marqueur == 0xD9) {
        break;
      } else if (marqueur == 0x01 || (marqueur >= 0xD0 && marqueur <= 0xD7)) {
        i += 2;
      } else {
        int longueur = u16(jpeg, i + 2, true);
        int fin = i + 2 + longueur;
        if (longueur < 2 || fin > jpeg.length) {
          break;
        }
        if (marqueur == 0xE1 && longueur >= 8 && commencePar(jpeg, i + 4, EN_TETE_EXIF)) {
          return orientationTiff(jpeg, i + 10, fin);
        }
        i = fin;
      }
    }
    return 1;
  }

  /** TIFF de {@code debut} à {@code fin} (exclu) : balise 0x0112 de l'IFD0. */
  private static int orientationTiff(byte[] o, int debut, int fin) {
    if (fin - debut < 10) {
      return 1;
    }
    boolean grandBoutiste;
    if (o[debut] == 'M' && o[debut + 1] == 'M') {
      grandBoutiste = true;
    } else if (o[debut] == 'I' && o[debut + 1] == 'I') {
      grandBoutiste = false;
    } else {
      return 1;
    }
    long ifd = u32(o, debut + 4, grandBoutiste);
    if (ifd < 8 || ifd > fin - debut - 2) {
      return 1;
    }
    int entrees = u16(o, debut + (int) ifd, grandBoutiste);
    int premiere = debut + (int) ifd + 2;
    for (int n = 0; n < entrees && premiere + 12 * (n + 1) <= fin; n++) {
      int e = premiere + 12 * n;
      if (u16(o, e, grandBoutiste) == 0x0112 && u16(o, e + 2, grandBoutiste) == 3) {
        int valeur = u16(o, e + 8, grandBoutiste);
        return valeur >= 1 && valeur <= 8 ? valeur : 1;
      }
    }
    return 1;
  }

  private static int u16(byte[] o, int i, boolean grandBoutiste) {
    int a = o[i] & 0xFF;
    int b = o[i + 1] & 0xFF;
    return grandBoutiste ? (a << 8) | b : (b << 8) | a;
  }

  private static long u32(byte[] o, int i, boolean grandBoutiste) {
    long haut = u16(o, grandBoutiste ? i : i + 2, grandBoutiste);
    long bas = u16(o, grandBoutiste ? i + 2 : i, grandBoutiste);
    return (haut << 16) | bas;
  }

  /**
   * Rend l'image telle qu'on doit la voir : miroirs et rotations de la norme EXIF. Les orientations
   * 5 à 8 permutent largeur et hauteur ; le plafond de côté reste valable, il porte sur le plus
   * grand des deux.
   */
  private static BufferedImage appliquerOrientation(BufferedImage image, int orientation) {
    if (orientation == 1) {
      return image;
    }
    int l = image.getWidth();
    int h = image.getHeight();
    // AffineTransform(m00, m10, m01, m11, m02, m12) : x' = m00·x + m01·y + m02, y' = m10·x + m11·y
    // + m12.
    AffineTransform t =
        switch (orientation) {
          case 2 -> new AffineTransform(-1, 0, 0, 1, l, 0);
          case 3 -> new AffineTransform(-1, 0, 0, -1, l, h);
          case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
          case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
          case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
          case 7 -> new AffineTransform(0, -1, -1, 0, h, l);
          default -> new AffineTransform(0, -1, 1, 0, 0, l);
        };
    boolean permute = orientation >= 5;
    BufferedImage tournee =
        new BufferedImage(permute ? h : l, permute ? l : h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = tournee.createGraphics();
    try {
      g.drawImage(image, t, null);
    } finally {
      g.dispose();
    }
    return tournee;
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
    return commencePar(octets, 0, debut);
  }

  private static boolean commencePar(byte[] octets, int decalage, byte[] debut) {
    if (decalage < 0 || octets.length < decalage + debut.length) {
      return false;
    }
    for (int i = 0; i < debut.length; i++) {
      if (octets[decalage + i] != debut[i]) {
        return false;
      }
    }
    return true;
  }

  private static ErreurMetier refus(String code, String message) {
    return new ErreurMetier(code, TypeErreur.INVALIDE, message);
  }
}
