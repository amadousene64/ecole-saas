package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.ECOLE_INACTIVE;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Activation d'un compte parent par code : usage unique, expiration, force brute, isolement. */
class ActivationApiTest extends TestInscription {

  private static final String ACTIVATIONS = "/api/v1/activations";

  private UUID eleve;
  private UUID responsable;

  @BeforeEach
  void responsableLie() throws Exception {
    eleve = creerEleve(A, "Awa");
    responsable = creerResponsable(SECRETARIAT_A, A, "Mère", "770000001");
    lier(SECRETARIAT_A, A, eleve, responsable, "MERE", true);
  }

  private String emettre(UUID qui, UUID ecole, UUID resp) throws Exception {
    Reponse r = post(qui, ecole, "/api/v1/responsables/" + resp + "/codes-activation", null);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    assertThat(r.brut().getHeader("Cache-Control")).isEqualTo("no-store");
    return r.corps().path("code").asText();
  }

  private Reponse activer(UUID compte, UUID ecole, String code) throws Exception {
    return activer(compte, ecole, code, "192.0.2.10");
  }

  private Reponse activer(UUID compte, UUID ecole, String code, String ip) throws Exception {
    return activer(compte, ecole, code, ip, "parent");
  }

  private Reponse activer(UUID compte, UUID ecole, String code, String ip, String client)
      throws Exception {
    var requete =
        MockMvcRequestBuilders.post(ACTIVATIONS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("code", code)))
            .with(jwt().jwt(j -> j.subject(compte.toString()).claim("azp", client)))
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                });
    if (ecole != null) {
      requete.header("X-Tenant-Id", ecole.toString());
    }
    return new Reponse(mockMvc.perform(requete).andReturn().getResponse());
  }

  /** Remplace le secret par un autre de même forme (même identifiant). */
  private static String fauxSecret(String code) {
    return code.substring(0, 5) + (code.charAt(5) == '2' ? '3' : '2') + code.substring(6);
  }

  @Test
  void unNouveauCompteDevientParentDeSesEnfantsEtDeRienDAutre() throws Exception {
    String code = emettre(SECRETARIAT_A, A, responsable);
    assertThat(code).matches("[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}");
    UUID compte = UUID.randomUUID();
    // Avant activation : aucun accès à l'école.
    assertThat(get(compte, A, "/api/v1/moi/enfants").code()).isEqualTo("TENANT_NON_AUTORISE");

    Reponse r = activer(compte, A, code.toLowerCase().replace("-", " "));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    assertThat(r.corps().path("role").asText()).isEqualTo("PARENT");

    assertThat(get(compte, A, "/api/v1/moi/enfants").corps()).hasSize(1);
    assertThat(get(compte, A, ELEVES + "/" + eleve).statut()).isEqualTo(200);
    assertThat(get(compte, A, ELEVES).statut()).isEqualTo(403);
    assertThat(
            get(DIRECTEUR_A, A, "/api/v1/responsables/" + responsable)
                .corps()
                .path("compteActive")
                .asBoolean())
        .isTrue();
    assertThat(journal("COMPTE_ACTIVE")).hasSize(1);
    assertThat(journal("ROLE_ATTRIBUE")).hasSize(1);
  }

  @Test
  void leCodeNEstStockeQueSousFormeDEmpreinte() throws Exception {
    String code = emettre(SECRETARIAT_A, A, responsable);
    Map<String, Object> ligne =
        superUtilisateur()
            .queryForMap(
                "SELECT identifiant, empreinte FROM ecole.code_activation WHERE cible_id = ?",
                responsable);
    assertThat(ligne.get("identifiant")).isEqualTo(code.substring(0, 4));
    String secret = code.substring(5).replace("-", "");
    assertThat(new String((byte[]) ligne.get("empreinte"), StandardCharsets.ISO_8859_1))
        .doesNotContain(secret);
    assertThat((String) journal("CODE_ACTIVATION_EMIS").get(0).get("details"))
        .doesNotContain(secret);
  }

  @Test
  void seuleLApplicationParentActiveUnCode() throws Exception {
    String code = emettre(SECRETARIAT_A, A, responsable);
    // Compte créé par auto-inscription sur un autre client : refusé, et le code reste valable.
    for (String client : new String[] {"backoffice", "vie-scolaire"}) {
      assertThat(activer(UUID.randomUUID(), A, code, "192.0.2.10", client).statut()).isEqualTo(403);
    }
    assertThat(activer(UUID.randomUUID(), A, code, "192.0.2.10", null).statut()).isEqualTo(403);
    assertThat(activer(UUID.randomUUID(), A, code).statut()).isEqualTo(200);
  }

  @Test
  void unCodeNeSertQuUneFois() throws Exception {
    String code = emettre(SECRETARIAT_A, A, responsable);
    assertThat(activer(UUID.randomUUID(), A, code).statut()).isEqualTo(200);
    Reponse rejeu = activer(UUID.randomUUID(), A, code);
    assertThat(rejeu.statut()).isEqualTo(400);
    assertThat(rejeu.code()).isEqualTo("ACTIVATION_IMPOSSIBLE");
    // Plus de nouveau code pour un responsable déjà relié.
    assertThat(
            post(
                    SECRETARIAT_A,
                    A,
                    "/api/v1/responsables/" + responsable + "/codes-activation",
                    null)
                .code())
        .isEqualTo("COMPTE_DEJA_RELIE");
  }

  @Test
  void unCodeExpireEstRefuseAvecLaMemeReponse() throws Exception {
    String code = emettre(SECRETARIAT_A, A, responsable);
    superUtilisateur()
        .update(
            "UPDATE ecole.code_activation SET expire_le = now() - interval '1 minute' WHERE cible_id = ?",
            responsable);
    Reponse r = activer(UUID.randomUUID(), A, code);
    assertThat(r.code()).isEqualTo("ACTIVATION_IMPOSSIBLE");
    Reponse inconnu = activer(UUID.randomUUID(), A, "ZZZZ-ZZZZ-ZZZZ");
    assertThat(inconnu.corps().path("detail").asText())
        .isEqualTo(r.corps().path("detail").asText());
    assertThat(journal("ACTIVATION_REFUSEE"))
        .extracting(j -> (String) j.get("details"))
        .anySatisfy(d -> assertThat(d).contains("EXPIRE"))
        .anySatisfy(d -> assertThat(d).contains("INCONNU"));
  }

  @Test
  void unNouveauCodeRevoqueLePrecedent() throws Exception {
    String premier = emettre(SECRETARIAT_A, A, responsable);
    String second = emettre(DIRECTEUR_A, A, responsable);
    assertThat(activer(UUID.randomUUID(), A, premier).code()).isEqualTo("ACTIVATION_IMPOSSIBLE");
    assertThat(activer(UUID.randomUUID(), A, second).statut()).isEqualTo(200);
  }

  @Test
  void forceBruteLeCodeSEpuiseApresCinqEssaisFaux() throws Exception {
    String code = emettre(SECRETARIAT_A, A, responsable);
    // Cinq comptes et cinq adresses différents : seul le compteur du code joue.
    for (int i = 0; i < 5; i++) {
      Reponse r = activer(UUID.randomUUID(), A, fauxSecret(code), "192.0.2." + (20 + i));
      assertThat(r.code()).isEqualTo("ACTIVATION_IMPOSSIBLE");
    }
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT statut FROM ecole.code_activation WHERE cible_id = ?",
                    String.class,
                    responsable))
        .isEqualTo("EPUISE");
    assertThat(activer(UUID.randomUUID(), A, code, "192.0.2.99").code())
        .isEqualTo("ACTIVATION_IMPOSSIBLE");
  }

  @Test
  void forceBruteDixEssaisParHeureEtParCompte() throws Exception {
    UUID compte = UUID.randomUUID();
    for (int i = 0; i < 10; i++) {
      assertThat(activer(compte, A, "ZZZZ-ZZZZ-ZZZ" + i % 10, "192.0.2." + (30 + i)).statut())
          .isEqualTo(400);
    }
    String code = emettre(SECRETARIAT_A, A, responsable);
    Reponse bloque = activer(compte, A, code, "192.0.2.50");
    assertThat(bloque.statut()).isEqualTo(429);
    assertThat(bloque.code()).isEqualTo("TROP_DE_TENTATIVES");
    // Le bon code reste utilisable par un autre compte : le blocage ne l'a pas consommé.
    assertThat(activer(UUID.randomUUID(), A, code, "192.0.2.51").statut()).isEqualTo(200);
  }

  @Test
  void forceBruteVingtEssaisParHeureEtParAdresseIp() throws Exception {
    for (int i = 0; i < 20; i++) {
      assertThat(activer(UUID.randomUUID(), A, "ZZZZ-ZZZZ-ZZZZ", "198.51.100.7").statut())
          .isEqualTo(400);
    }
    String code = emettre(SECRETARIAT_A, A, responsable);
    assertThat(activer(UUID.randomUUID(), A, code, "198.51.100.7").code())
        .isEqualTo("TROP_DE_TENTATIVES");
    assertThat(activer(UUID.randomUUID(), A, code, "198.51.100.8").statut()).isEqualTo(200);
  }

  @Test
  void unCodeDeLEcoleANeMarchePasDansLEcoleB() throws Exception {
    String code = emettre(SECRETARIAT_A, A, responsable);
    UUID compte = UUID.randomUUID();
    assertThat(activer(compte, B, code).code()).isEqualTo("ACTIVATION_IMPOSSIBLE");
    assertThat(get(compte, B, "/api/v1/moi/enfants").code()).isEqualTo("TENANT_NON_AUTORISE");
    assertThat(activer(compte, A, code).statut()).isEqualTo(200);
    // Le directeur de B ne peut pas émettre de code pour un responsable de A.
    assertThat(
            post(DIRECTEUR_B, B, "/api/v1/responsables/" + responsable + "/codes-activation", null)
                .statut())
        .isEqualTo(404);
  }

  @Test
  void sansEcoleOuDansUneEcoleInactiveLActivationEstRefusee() throws Exception {
    String code = emettre(SECRETARIAT_A, A, responsable);
    assertThat(activer(UUID.randomUUID(), null, code).code()).isEqualTo("TENANT_ABSENT");
    assertThat(activer(UUID.randomUUID(), ECOLE_INACTIVE, code).code())
        .isEqualTo("TENANT_NON_AUTORISE");
    var sansJeton =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post(ACTIVATIONS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + code + "\"}")
                    .header("X-Tenant-Id", A.toString()))
            .andReturn();
    assertThat(sansJeton.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void unCompteDejaRelieAUnAutreResponsableEstRefuse() throws Exception {
    UUID autre = creerResponsable(SECRETARIAT_A, A, "Tante", "770000009");
    UUID compte = UUID.randomUUID();
    assertThat(activer(compte, A, emettre(SECRETARIAT_A, A, responsable)).statut()).isEqualTo(200);
    Reponse r = activer(compte, A, emettre(SECRETARIAT_A, A, autre));
    assertThat(r.code()).isEqualTo("COMPTE_DEJA_UTILISE");
  }

  @Test
  void seulsLaDirectionEtLeSecretariatEmettentDesCodes() throws Exception {
    for (UUID qui :
        new UUID[] {
          sn.ecole.Fixtures.CENSEUR_A,
          sn.ecole.Fixtures.SURVEILLANT_A,
          sn.ecole.Fixtures.COMPTABLE_A,
          sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B,
          sn.ecole.Fixtures.PARENT_A,
          sn.ecole.Fixtures.ELEVE_A
        }) {
      assertThat(
              post(qui, A, "/api/v1/responsables/" + responsable + "/codes-activation", null)
                  .statut())
          .isEqualTo(403);
    }
  }
}
