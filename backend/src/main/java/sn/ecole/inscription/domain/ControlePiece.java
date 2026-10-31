package sn.ecole.inscription.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import sn.ecole.shared.domain.ControleImage;

/**
 * Contrôle d'une pièce déposée : taille bornée, type déterminé par les octets magiques (jamais par
 * l'en-tête HTTP ni par le nom du fichier). PDF, JPEG et PNG seulement. Une image est décodée puis
 * ré-encodée (comme le logo) : plus aucune métadonnée (EXIF, position GPS, appareil) ni contenu
 * caché ; une photo de téléphone est réduite à {@link #COTE_IMAGE_MAX} pixels de côté. Un PDF est
 * gardé tel quel. L'analyse antivirus décide ensuite si la pièce peut être servie.
 */
public final class ControlePiece {

  /** Taille maximale d'une pièce (octets). */
  public static final int TAILLE_MAX = 5_000_000;

  /** Côté maximal d'une image stockée (pixels) : lisible pour un acte, sans excès. */
  public static final int COTE_IMAGE_MAX = 2_500;

  private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  /**
   * Pièce acceptée : octets à stocker (ré-encodés pour une image), type réel, taille et empreinte
   * SHA-256 de ces octets.
   */
  public record PieceValidee(byte[] octets, String contentType, int taille, String sha256) {}

  private ControlePiece() {}

  public static PieceValidee controler(byte[] contenu) {
    if (contenu == null || contenu.length == 0) {
      throw Saisies.invalide("FICHIER_INVALIDE", "Le fichier est vide.");
    }
    if (contenu.length > TAILLE_MAX) {
      throw Saisies.invalide("FICHIER_TROP_VOLUMINEUX", "Fichier trop volumineux (5 Mo au plus).");
    }
    byte[] octets;
    String type;
    if (commencePar(contenu, PDF)) {
      octets = contenu;
      type = "application/pdf";
    } else if (commencePar(contenu, PNG) || commencePar(contenu, JPEG)) {
      ControleImage.ImageValidee image = ControleImage.reduire(contenu, TAILLE_MAX, COTE_IMAGE_MAX);
      octets = image.octets();
      type = image.contentType();
      if (octets.length > TAILLE_MAX) {
        throw Saisies.invalide(
            "FICHIER_TROP_VOLUMINEUX", "Image trop volumineuse une fois traitée (5 Mo au plus).");
      }
    } else {
      throw Saisies.invalide(
          "FICHIER_INVALIDE", "Seuls les formats PDF, JPEG et PNG sont acceptés.");
    }
    return new PieceValidee(octets, type, octets.length, sha256(octets));
  }

  static String extension(String contentType) {
    return switch (contentType) {
      case "application/pdf" -> "pdf";
      case "image/png" -> "png";
      case "image/jpeg" -> "jpg";
      default -> "bin";
    };
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

  public static String sha256(byte[] contenu) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
