package sn.ecole.shared.infrastructure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.ecole.shared.application.ChiffrementDonnees;
import sn.ecole.shared.application.EmpreinteSecrets;

/**
 * Clés de chiffrement et d'empreinte : contrôlées au démarrage. Absentes, mal formées ou égales
 * entre elles, l'application ne démarre pas (pas de mode dégradé qui stockerait en clair).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClesProperties.class)
class ClesConfig {

  @Bean
  ChiffrementDonnees chiffrementDonnees(ClesProperties p) {
    byte[] active = Cles.decoder(p.chiffrement(), "ecole.cles.chiffrement");
    byte[] hmac = Cles.decoder(p.hmacCodes(), "ecole.cles.hmac-codes");
    if (Cles.identiques(active, hmac)) {
      throw new IllegalStateException("Les clés de chiffrement et HMAC doivent être distinctes.");
    }
    Map<String, byte[]> anciennes = new HashMap<>();
    p.anciennesCles()
        .forEach((id, cle) -> anciennes.put(id, Cles.decoder(cle, "ecole.cles.anciennes-cles")));
    return new ChiffrementAesGcm(p.idChiffrement(), active, anciennes);
  }

  @Bean
  EmpreinteSecrets empreinteSecrets(ClesProperties p) {
    return new EmpreinteHmac(Cles.decoder(p.hmacCodes(), "ecole.cles.hmac-codes"));
  }
}
