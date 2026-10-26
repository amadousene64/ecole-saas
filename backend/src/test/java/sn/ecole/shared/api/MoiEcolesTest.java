package sn.ecole.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.MEMBRE_INACTIVE;
import static sn.ecole.Fixtures.SURVEILLANT_B;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import sn.ecole.Fixtures;
import sn.ecole.TestIntegration;
import tools.jackson.databind.json.JsonMapper;

/** GET /api/v1/moi/ecoles : un utilisateur ne voit que SES écoles, sans désigner d'école. */
class MoiEcolesTest extends TestIntegration {

  private static final String CHEMIN = "/api/v1/moi/ecoles";

  /** Utilisateur créé par certains tests ; ses lignes sont supprimées après chacun. */
  private final UUID utilisateurEssai = UUID.randomUUID();

  @Autowired MockMvcTester mvc;

  @AfterEach
  void nettoyer() {
    superUtilisateur()
        .update("DELETE FROM ecole.appartenance WHERE utilisateur_id = ?", utilisateurEssai);
  }

  private MvcTestResult appel(UUID utilisateur) {
    return mvc.get().uri(CHEMIN).with(jwt().jwt(j -> j.subject(utilisateur.toString()))).exchange();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> ecoles(MvcTestResult resultat) throws Exception {
    return new JsonMapper().readValue(resultat.getResponse().getContentAsString(), List.class);
  }

  private static List<String> ids(MvcTestResult resultat) throws Exception {
    return ecoles(resultat).stream().map(e -> (String) e.get("id")).toList();
  }

  @Test
  void sansJetonLaReponseEst401() {
    assertThat(mvc.get().uri(CHEMIN)).hasStatus(401);
  }

  @Test
  void unEnseignantDeAEtParentDeBVoitLesDeuxEcolesSansEnteteTenant() throws Exception {
    MvcTestResult resultat = appel(ENSEIGNANT_A_PARENT_B);
    assertThat(resultat).hasStatusOk();
    assertThat(ids(resultat)).containsExactlyInAnyOrder(ECOLE_A.toString(), ECOLE_B.toString());
    assertThat(ecoles(resultat))
        .extracting(e -> e.get("code"))
        .containsExactlyInAnyOrder("LYCEE-A", "ECOLE-B");
    assertThat(ecoles(resultat)).allSatisfy(e -> assertThat(e).containsKeys("id", "code", "nom"));
  }

  @Test
  void unUtilisateurNeVoitQueSesEcoles() throws Exception {
    assertThat(ids(appel(DIRECTEUR_A))).containsExactly(ECOLE_A.toString());
    assertThat(ids(appel(SURVEILLANT_B))).containsExactly(ECOLE_B.toString());
  }

  @Test
  void unUtilisateurSansAppartenanceNeVoitAucuneEcole() throws Exception {
    MvcTestResult resultat = appel(UUID.randomUUID());
    assertThat(resultat).hasStatusOk();
    assertThat(ecoles(resultat)).isEmpty();
  }

  @Test
  void uneEcoleInactiveNEstPasProposee() throws Exception {
    assertThat(ecoles(appel(MEMBRE_INACTIVE))).isEmpty();
  }

  @Test
  void uneAppartenanceRetireeFaitDisparaitreLEcole() throws Exception {
    Fixtures.appartenance(superUtilisateur(), ECOLE_A, utilisateurEssai, "ENSEIGNANT");
    Fixtures.appartenance(superUtilisateur(), ECOLE_B, utilisateurEssai, "PARENT");
    assertThat(ids(appel(utilisateurEssai)))
        .containsExactlyInAnyOrder(ECOLE_A.toString(), ECOLE_B.toString());

    superUtilisateur()
        .update(
            "UPDATE ecole.appartenance SET supprime_le = now()"
                + " WHERE utilisateur_id = ? AND tenant_id = ?",
            utilisateurEssai,
            ECOLE_B);

    assertThat(ids(appel(utilisateurEssai))).containsExactly(ECOLE_A.toString());
  }

  @Test
  void plusieursRolesDansLaMemeEcoleNeDonnentQueDeuxLignesDistinctes() throws Exception {
    Fixtures.appartenance(superUtilisateur(), ECOLE_A, utilisateurEssai, "ENSEIGNANT");
    Fixtures.appartenance(superUtilisateur(), ECOLE_A, utilisateurEssai, "SURVEILLANT");
    assertThat(ids(appel(utilisateurEssai))).containsExactly(ECOLE_A.toString());
  }

  @Test
  void l_identifiantVientDuJetonEtJamaisDeLaRequete() throws Exception {
    // Ni en-tête d'école, ni paramètres de requête ne changent l'utilisateur interrogé.
    MvcTestResult resultat =
        mvc.get()
            .uri(
                CHEMIN + "?utilisateurId={a}&sub={b}&userId={c}",
                ENSEIGNANT_A_PARENT_B,
                ENSEIGNANT_A_PARENT_B,
                ENSEIGNANT_A_PARENT_B)
            .header("X-Tenant-Id", ECOLE_B.toString())
            .header("X-User-Id", ENSEIGNANT_A_PARENT_B.toString())
            .with(jwt().jwt(j -> j.subject(DIRECTEUR_A.toString())))
            .exchange();
    assertThat(resultat).hasStatusOk();
    assertThat(ids(resultat)).containsExactly(ECOLE_A.toString());
  }

  @Test
  void uneRevendicationDuJetonNeDonnePasAccesAuxEcolesDUnAutre() throws Exception {
    MvcTestResult resultat =
        mvc.get()
            .uri(CHEMIN)
            .with(
                jwt()
                    .jwt(
                        j ->
                            j.subject(SURVEILLANT_B.toString())
                                .claim("utilisateur_id", ENSEIGNANT_A_PARENT_B.toString())
                                .claim("tenants", List.of(ECOLE_A.toString()))))
            .exchange();
    assertThat(ids(resultat)).containsExactly(ECOLE_B.toString());
  }

  @Test
  void unSubQuiNEstPasUnUuidEstRefuse() throws Exception {
    MvcTestResult resultat =
        mvc.get().uri(CHEMIN).with(jwt().jwt(j -> j.subject("n-importe-quoi"))).exchange();
    assertThat(resultat).hasStatus(401);
    assertThat(new JsonMapper().readValue(resultat.getResponse().getContentAsString(), Map.class))
        .containsEntry("code", "JETON_INVALIDE");
  }

  @Test
  void lExceptionSansEcoleNeConcerneQueCetAppel() {
    // /moi exige toujours une école ; une variante du chemin ou une autre méthode aussi.
    assertThat(mvc.get().uri("/api/v1/moi").with(jwt().jwt(j -> j.subject(DIRECTEUR_A.toString()))))
        .hasStatus(400);
    assertThat(mvc.post().uri(CHEMIN).with(jwt().jwt(j -> j.subject(DIRECTEUR_A.toString()))))
        .hasStatus(400);
    assertThat(mvc.get().uri(CHEMIN + "/").with(jwt().jwt(j -> j.subject(DIRECTEUR_A.toString()))))
        .hasStatus(400);
  }

  @Test
  void leContratNeDemandePasXTenantIdPourCetAppel() throws Exception {
    String contrat = mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString();
    assertThat(contrat).contains(CHEMIN);
    var json = new JsonMapper().readTree(contrat);
    assertThat(
            json.at("/paths/~1api~1v1~1moi~1ecoles/get/parameters").isMissingNode()
                || json.at("/paths/~1api~1v1~1moi~1ecoles/get/parameters").isEmpty())
        .isTrue();
  }

  @Test
  void unJetonSansSubEstRefuseEn401() {
    assertThat(
            mvc.get()
                .uri(CHEMIN)
                .with(jwt().jwt(j -> j.claims(c -> c.remove("sub")).claim("azp", "backoffice"))))
        .hasStatus(401);
  }

  @Test
  void uneEcoleSupprimeeNEstPasProposee() throws Exception {
    Fixtures.appartenance(superUtilisateur(), ECOLE_A, utilisateurEssai, "ENSEIGNANT");
    UUID ecoleSupprimee = UUID.randomUUID();
    superUtilisateur()
        .update(
            "INSERT INTO ecole.tenant (id, code, nom, sous_domaine, supprime_le)"
                + " VALUES (?, 'ECOLE-S', 'École Supprimée', 'ecole-s', now())",
            ecoleSupprimee);
    try {
      Fixtures.appartenance(superUtilisateur(), ecoleSupprimee, utilisateurEssai, "PARENT");
      assertThat(ids(appel(utilisateurEssai))).containsExactly(ECOLE_A.toString());
    } finally {
      superUtilisateur()
          .update("DELETE FROM ecole.appartenance WHERE tenant_id = ?", ecoleSupprimee);
      superUtilisateur().update("DELETE FROM ecole.tenant WHERE id = ?", ecoleSupprimee);
    }
  }

  @Test
  void lesVariantesDuCheminNeDonnentJamaisAccesSansEcole() {
    for (String variante :
        List.of("/api/v1/moi/%65coles", "/api/v1/moi/ecoles;x=1", "/API/v1/moi/ecoles")) {
      MvcTestResult resultat =
          mvc.get()
              .uri(variante)
              .with(jwt().jwt(j -> j.subject(DIRECTEUR_A.toString())))
              .exchange();
      assertThat(resultat.getResponse().getStatus()).as(variante).isNotEqualTo(200);
    }
    assertThat(
            mvc.method(org.springframework.http.HttpMethod.HEAD)
                .uri(CHEMIN)
                .with(jwt().jwt(j -> j.subject(DIRECTEUR_A.toString()))))
        .hasStatus(400);
  }
}
