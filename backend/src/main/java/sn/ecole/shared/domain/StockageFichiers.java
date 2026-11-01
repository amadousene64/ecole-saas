package sn.ecole.shared.domain;

import java.net.URI;
import java.time.Duration;

/**
 * Port du stockage d'objets (MinIO). Les clés sont construites par le serveur ; l'accès en lecture
 * se fait uniquement par URL signée à durée limitée.
 */
public interface StockageFichiers {

  void deposer(String cle, byte[] contenu, String contentType);

  void supprimer(String cle);

  /** Contenu d'un objet (analyse antivirus) ; {@code IllegalStateException} s'il n'existe pas. */
  byte[] lire(String cle);

  URI urlSignee(String cle, Duration validite);

  /**
   * URL signée qui force le téléchargement ({@code Content-Disposition: attachment}) avec le type
   * donné : le navigateur n'interprète jamais le fichier dans l'origine du stockage.
   */
  URI urlTelechargement(String cle, Duration validite, String contentType, String nomFichier);
}
