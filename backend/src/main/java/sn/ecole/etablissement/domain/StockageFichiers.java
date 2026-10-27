package sn.ecole.etablissement.domain;

import java.net.URI;
import java.time.Duration;

/**
 * Port du stockage d'objets (MinIO). Les clés sont construites par le serveur ; l'accès en lecture
 * se fait uniquement par URL signée à durée limitée.
 */
public interface StockageFichiers {

  void deposer(String cle, byte[] contenu, String contentType);

  void supprimer(String cle);

  URI urlSignee(String cle, Duration validite);
}
