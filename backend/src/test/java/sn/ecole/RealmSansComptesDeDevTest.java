package sn.ecole;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Garde-fou de mise en production (docs/mise-en-production.md). Le realm versionné est le realm de
 * DEV (clients localhost, dev-cli, Mailpit) : il n'est jamais importé tel quel en production, mais
 * il ne contient de toute façon aucun compte ni mot de passe. Les comptes fictifs vivent dans
 * infra/keycloak/dev/, monté par le seul docker-compose de dev (et lus par les scripts de dev).
 */
class RealmSansComptesDeDevTest {

  private static final Path RACINE = Path.of("..");
  private static final Path INFRA = RACINE.resolve("infra");

  /** Fichiers qui peuvent monter ou copier un fichier dans un conteneur, à toute profondeur. */
  private static final Pattern DEPLOIEMENT =
      Pattern.compile("(?i).*(\\.(ya?ml|sh|json|env|tf|tpl|conf)|dockerfile[^/]*|containerfile)$");

  private static final List<String> IGNORES =
      List.of("/node_modules/", "/.git/", "/target/", "/dist/", "/.nx/", "/.angular/");

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
  void lesComptesDeDevNeSontMontesQueParLeComposeEtLesScriptsDeDev() throws Exception {
    assertThat(INFRA.resolve("keycloak/dev/ecole-users-0.json")).exists();
    List<String> montages;
    try (Stream<Path> fichiers = Files.walk(RACINE)) {
      montages =
          fichiers
              .filter(Files::isRegularFile)
              .map(p -> p.normalize().toString().replace('\\', '/'))
              .filter(p -> IGNORES.stream().noneMatch(p::contains))
              .filter(p -> DEPLOIEMENT.matcher(p).matches())
              .filter(p -> !p.endsWith("/ecole-users-0.json"))
              .filter(RealmSansComptesDeDevTest::designeLesComptesDeDev)
              .toList();
    }
    // Le compose de dev, et les scripts de dev (qui refusent un Keycloak non local) ; rien d'autre.
    assertThat(montages)
        .contains("../infra/docker-compose.yml")
        .allMatch(
            p ->
                p.equals("../infra/docker-compose.yml")
                    || p.matches("\\.\\./infra/scripts/[a-z-]+-dev\\.sh"));
  }

  /** Le fichier des comptes, ou le dossier entier qui le contient (montage par répertoire). */
  private static boolean designeLesComptesDeDev(String chemin) {
    try {
      String texte = Files.readString(Path.of(chemin));
      return texte.contains("ecole-users-0.json")
          || texte.contains("keycloak/dev:")
          || texte.contains("keycloak/dev/:")
          || texte.matches("(?s).*\\./keycloak:/opt/keycloak/data/import.*");
    } catch (Exception e) {
      return false; // binaire ou illisible : ne monte rien
    }
  }
}
