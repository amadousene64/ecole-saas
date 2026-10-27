package sn.ecole.shared.infrastructure;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clés applicatives, toujours fournies par l'environnement (jamais dans le dépôt), en Base64 de 32
 * octets.
 *
 * @param chiffrement clé AES-256 active des données sensibles
 * @param idChiffrement identifiant de la clé active, écrit dans chaque chiffré (rotation)
 * @param anciennesCles clés retirées, encore utilisées pour relire (identifiant → clé)
 * @param hmacCodes clé HMAC des codes d'activation ; doit différer de la clé de chiffrement
 */
@ConfigurationProperties("ecole.cles")
record ClesProperties(
    String chiffrement, String idChiffrement, Map<String, String> anciennesCles, String hmacCodes) {

  ClesProperties {
    idChiffrement = idChiffrement == null || idChiffrement.isBlank() ? "k1" : idChiffrement;
    anciennesCles = anciennesCles == null ? Map.of() : Map.copyOf(anciennesCles);
  }
}
