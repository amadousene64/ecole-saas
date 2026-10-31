package sn.ecole.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.TestIntegration;

/**
 * Derrière le répartiteur de charge, l'adresse du client (limite de débit des activations par IP)
 * vient de X-Forwarded-For, mais seulement si la requête arrive d'un proxy de confiance : sinon un
 * attaquant changerait d'adresse à chaque essai. Vrai serveur Tomcat (port aléatoire).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdresseClientDerriereProxyTest.Essai.class)
class AdresseClientDerriereProxyTest extends TestIntegration {

  /** Point d'essai public qui renvoie l'adresse vue par l'application. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Essai {

    @Bean
    @Order(-1)
    SecurityFilterChain chaineEssai(HttpSecurity http) throws Exception {
      return http.securityMatcher("/essai-ip")
          .authorizeHttpRequests(r -> r.anyRequest().permitAll())
          .build();
    }

    @Bean
    ControleurIp controleurIp() {
      return new ControleurIp();
    }
  }

  @RestController
  static class ControleurIp {
    @GetMapping("/essai-ip")
    String ip(HttpServletRequest requete) {
      return requete.getRemoteAddr();
    }
  }

  @LocalServerPort int port;
  @Autowired WebServerApplicationContext contexte;

  private String appeler(String xForwardedFor) throws Exception {
    HttpRequest.Builder requete =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/essai-ip"));
    if (xForwardedFor != null) {
      requete.header("X-Forwarded-For", xForwardedFor);
    }
    return HttpClient.newHttpClient()
        .send(requete.build(), HttpResponse.BodyHandlers.ofString())
        .body();
  }

  @Test
  void depuisUnProxyDeConfianceLAdresseDuClientEstCelleDeXForwardedFor() throws Exception {
    assertThat(appeler(null)).isEqualTo("127.0.0.1");
    // La requête vient de 127.0.0.1 (proxy de confiance) : le client réel est 203.0.113.7.
    assertThat(appeler("203.0.113.7")).isEqualTo("203.0.113.7");
    // Chaîne de proxys : le premier client non interne en partant de la fin.
    assertThat(appeler("198.51.100.4, 203.0.113.7")).isEqualTo("203.0.113.7");
  }

  @Test
  void uneAdressePubliqueNEstJamaisUnProxyDeConfiance() {
    RemoteIpValve valve =
        Arrays.stream(
                ((TomcatWebServer) contexte.getWebServer())
                    .getTomcat()
                    .getEngine()
                    .getPipeline()
                    .getValves())
            .filter(RemoteIpValve.class::isInstance)
            .map(RemoteIpValve.class::cast)
            .findFirst()
            .orElseThrow();
    Pattern internes = Pattern.compile(valve.getInternalProxies());
    assertThat(internes.matcher("127.0.0.1").matches()).isTrue();
    // Un réseau privé entier n'est pas de confiance par défaut (conteneur voisin, passerelle).
    assertThat(internes.matcher("10.1.2.3").matches()).isFalse();
    assertThat(internes.matcher("172.17.0.1").matches()).isFalse();
    assertThat(internes.matcher("203.0.113.9").matches()).isFalse();
    assertThat(internes.matcher("8.8.8.8").matches()).isFalse();
  }
}
