package sn.ecole;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Garde-fou de mise en production (docs/mise-en-production.md) : le realm Keycloak versionné, celui
 * qu'une installation de production importerait, ne contient aucun compte ni mot de passe. Les
 * comptes fictifs de dev vivent dans infra/keycloak/dev/, monté par le seul docker-compose de dev.
 */
class RealmSansComptesDeDevTest {

  private static final Path INFRA = Path.of("..", "infra");

  @Test
  void leRealmVersionneNeContientNiCompteNiMotDePasse() throws Exception {
    String texte = Files.readString(INFRA.resolve("keycloak/realm-ecole.json"));
    JsonNode realm = JsonMapper.builder().build().readTree(texte);
    assertThat(realm.path("users").isMissingNode() || realm.path("users").isEmpty())
        .as("aucun compte dans le realm versionné")
        .isTrue();
    assertThat(texte).doesNotContain("\"credentials\"").doesNotContain("\"password\"");
  }

  @Test
  void lesComptesDeDevNeSontMontesQueParLeComposeDeDev() throws Exception {
    assertThat(INFRA.resolve("keycloak/dev/ecole-users-0.json")).exists();
    try (var fichiers = Files.walk(Path.of(".."), 4)) {
      var montages =
          fichiers
              .filter(p -> p.getFileName().toString().matches(".*\\.(ya?ml|sh|Dockerfile)$"))
              .filter(p -> !p.toString().contains("node_modules"))
              .filter(
                  p -> {
                    try {
                      return Files.readString(p).contains("ecole-users-0.json");
                    } catch (Exception e) {
                      return false;
                    }
                  })
              .map(p -> p.normalize().toString().replace('\\', '/'))
              .toList();
      // Le compose de dev, et les scripts de dev (qui refusent un Keycloak non local) ; rien
      // d'autre.
      assertThat(montages)
          .contains("../infra/docker-compose.yml")
          .allMatch(
              p ->
                  p.equals("../infra/docker-compose.yml")
                      || p.matches("\\.\\./infra/scripts/[a-z-]+-dev\\.sh"));
    }
  }
}
