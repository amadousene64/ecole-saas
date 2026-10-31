package sn.ecole.inscription.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Contrôle d'une pièce déposée : taille bornée, type déterminé par les octets magiques (jamais par
 * l'en-tête HTTP ni par le nom du fichier). PDF, JPEG et PNG seulement. Le contenu n'est pas
 * modifié : c'est l'analyse antivirus qui décide ensuite s'il peut être servi.
 */
public final class ControlePiece {

  /** Taille maximale d'une pièce (octets). */
  public static final int TAILLE_MAX = 5_000_000;

  private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  /** Pièce acceptée : type réel, taille et empreinte SHA-256 (doublons, intégrité). */
  public record PieceValidee(String contentType, int taille, String sha256) {}

  private ControlePiece() {}

  public static PieceValidee controler(byte[] contenu) {
    if (contenu == null || contenu.length == 0) {
      throw Saisies.invalide("FICHIER_INVALIDE", "Le fichier est vide.");
    }
    if (contenu.length > TAILLE_MAX) {
      throw Saisies.invalide("FICHIER_TROP_VOLUMINEUX", "Fichier trop volumineux (5 Mo au plus).");
    }
    String type;
    if (commencePar(contenu, PDF)) {
      type = "application/pdf";
    } else if (commencePar(contenu, PNG)) {
      type = "image/png";
    } else if (commencePar(contenu, JPEG)) {
      type = "image/jpeg";
    } else {
      throw Saisies.invalide(
          "FICHIER_INVALIDE", "Seuls les formats PDF, JPEG et PNG sont acceptés.");
    }
    return new PieceValidee(type, contenu.length, sha256(contenu));
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

  private static String sha256(byte[] contenu) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
