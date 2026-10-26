package sn.ecole;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Le contrat OpenAPI versionné ({@code docs/api/openapi.json}) doit correspondre au code. Pour le
 * mettre à jour : {@code ./mvnw test -Dtest=ContratOpenApiTest -Dopenapi.maj=true}.
 */
class ContratOpenApiTest extends TestIntegration {

  private static final Path CONTRAT = Path.of("..", "docs", "api", "openapi.json");

  @Autowired MockMvcTester mvc;

  @Test
  void leContratVersionneEstAJour() throws Exception {
    String brut = mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString();
    JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    JsonNode genere = json.readTree(brut);
    // L'URL du serveur dépend du port de test : on la retire du contrat.
    if (genere.isObject()) {
      ((tools.jackson.databind.node.ObjectNode) genere).remove("servers");
    }
    String attendu = json.writeValueAsString(genere) + "\n";

    if (Boolean.getBoolean("openapi.maj")) {
      Files.createDirectories(CONTRAT.getParent());
      Files.writeString(CONTRAT, attendu, StandardCharsets.UTF_8);
    }
    assertThat(CONTRAT)
        .as("docs/api/openapi.json absent ou périmé : relancez avec -Dopenapi.maj=true")
        .exists();
    assertThat(json.readTree(Files.readString(CONTRAT, StandardCharsets.UTF_8))).isEqualTo(genere);
  }
}
