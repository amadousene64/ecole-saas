package sn.ecole.etablissement.infrastructure;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stockage d'objets. {@code endpointPublic} est l'adresse que voit le navigateur pour les URL
 * signées (si elle diffère de {@code endpoint}, vue par le serveur). Les clés d'accès viennent de
 * l'environnement, jamais du dépôt.
 */
@ConfigurationProperties("ecole.stockage")
record StockageProperties(
    URI endpoint,
    URI endpointPublic,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean creerBucket) {

  StockageProperties {
    region = region == null || region.isBlank() ? "us-east-1" : region;
    bucket = bucket == null || bucket.isBlank() ? "ecole-fichiers" : bucket;
  }

  URI endpointPourUrlSignee() {
    return endpointPublic != null && !endpointPublic.toString().isBlank()
        ? endpointPublic
        : endpoint;
  }
}
