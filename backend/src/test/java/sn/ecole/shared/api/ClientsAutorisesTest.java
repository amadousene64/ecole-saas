package sn.ecole.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Seuls les jetons des clients Keycloak autorisés (azp) sont acceptés, selon le profil. */
class ClientsAutorisesTest {

  private static final String EMETTEUR = "http://localhost:8180/realms/ecole";
  private static final Set<String> CLIENTS_PROD = Set.of("backoffice", "parent", "vie-scolaire");

  private final RSAKey cle = genererCle();
  private final NimbusJwtEncoder encodeur =
      NimbusJwtEncoder.withKeyPair(publique(), privee()).build();

  private static RSAKey genererCle() {
    try {
      return new RSAKeyGenerator(2048).keyID("essai").generate();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private java.security.interfaces.RSAPublicKey publique() {
    try {
      return cle.toRSAPublicKey();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private java.security.interfaces.RSAPrivateKey privee() {
    try {
      return cle.toRSAPrivateKey();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String jeton(Map<String, Object> revendications) {
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(EMETTEUR)
            .subject("01930000-0000-7000-8000-000000000101")
            .audience(List.of("ecole-api"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300));
    revendications.forEach(claims::claim);
    return encodeur
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
        .getTokenValue();
  }

  private NimbusJwtDecoder decodeur(Set<String> clients) {
    NimbusJwtDecoder decodeur = NimbusJwtDecoder.withPublicKey(publique()).build();
    decodeur.setJwtValidator(ValidateursJeton.pour(EMETTEUR, List.of("ecole-api"), clients));
    return decodeur;
  }

  @Test
  void unJetonDUnClientAutoriseEstAccepte() {
    assertThat(decodeur(CLIENTS_PROD).decode(jeton(Map.of("azp", "backoffice"))).getSubject())
        .isEqualTo("01930000-0000-7000-8000-000000000101");
  }

  @Test
  void horsProfilDevLeClientDevCliEstRefuse() {
    String jetonDevCli = jeton(Map.of("azp", "dev-cli"));
    assertThatThrownBy(() -> decodeur(CLIENTS_PROD).decode(jetonDevCli))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("Client non autorisé");
    assertThat(decodeur(Set.of("backoffice", "dev-cli")).decode(jetonDevCli)).isNotNull();
  }

  @Test
  void unJetonSansAzpOuDUnClientInconnuEstRefuse() {
    assertThatThrownBy(() -> decodeur(CLIENTS_PROD).decode(jeton(Map.of())))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decodeur(CLIENTS_PROD).decode(jeton(Map.of("azp", "account"))))
        .isInstanceOf(JwtValidationException.class);
  }

  @Test
  void lAudienceEtLEmetteurSontToujoursVerifies() {
    NimbusJwtDecoder decodeur = decodeur(CLIENTS_PROD);
    String autreAudience =
        encodeur
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).build(),
                    JwtClaimsSet.builder()
                        .issuer(EMETTEUR)
                        .subject("x")
                        .audience(List.of("account"))
                        .claim("azp", "backoffice")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build()))
            .getTokenValue();
    assertThatThrownBy(() -> decodeur.decode(autreAudience))
        .hasMessageContaining("Audience du jeton");

    String autreEmetteur =
        encodeur
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).build(),
                    JwtClaimsSet.builder()
                        .issuer("http://pirate.test/realms/ecole")
                        .subject("x")
                        .audience(List.of("ecole-api"))
                        .claim("azp", "backoffice")
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build()))
            .getTokenValue();
    assertThatThrownBy(() -> decodeur.decode(autreEmetteur))
        .isInstanceOf(JwtValidationException.class);
  }

  private static Properties yaml(String fichier) {
    YamlPropertiesFactoryBean usine = new YamlPropertiesFactoryBean();
    usine.setResources(new ClassPathResource(fichier));
    return usine.getObject();
  }

  @Test
  void devCliNEstDansLaListeQuEnProfilDev() {
    Properties defaut = yaml("application.yml");
    Properties dev = yaml("application-dev.yml");
    String cle = "ecole.securite.jeton.clients-autorises";
    assertThat(defaut.stringPropertyNames().stream().filter(k -> k.startsWith(cle)))
        .map(defaut::getProperty)
        .containsExactlyInAnyOrder("backoffice", "parent", "vie-scolaire");
    assertThat(dev.stringPropertyNames().stream().filter(k -> k.startsWith(cle)))
        .map(dev::getProperty)
        .contains("dev-cli");
  }

  @Test
  void uneListeVideEstRefuseeAuDemarrage() {
    assertThatThrownBy(() -> new JetonProperties(List.of(), null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new JetonProperties(null, null))
        .isInstanceOf(IllegalStateException.class);
  }
}
