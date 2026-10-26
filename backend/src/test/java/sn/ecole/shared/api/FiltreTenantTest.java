package sn.ecole.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;
import static sn.ecole.Fixtures.ECOLE_INACTIVE;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.MEMBRE_INACTIVE;
import static sn.ecole.Fixtures.SURVEILLANT_B;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.TestIntegration;

@Import(FiltreTenantTest.ControleurEssai.class)
class FiltreTenantTest extends TestIntegration {

  @Autowired MockMvcTester mvc;

  /** Endpoints d'essai, présents uniquement dans ce test. */
  @RestController
  static class ControleurEssai {

    private final EntityManager em;

    ControleurEssai(EntityManager em) {
      this.em = em;
    }

    /** JPQL volontairement sans filtre : l'isolement doit venir du socle. */
    @GetMapping("/api/essai/appartenances")
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<UUID> appartenances() {
      return em.createQuery("select a.utilisateurId from Appartenance a", UUID.class)
          .getResultList();
    }

    @GetMapping("/api/essai/enseignant")
    @PreAuthorize("hasRole('ENSEIGNANT')")
    public String reserveEnseignant() {
      return "ok";
    }

    @GetMapping("/api/essai/parent")
    @PreAuthorize("hasRole('PARENT')")
    public String reserveParent() {
      return "ok";
    }
  }

  private MvcTestResult appel(String chemin, UUID utilisateur, UUID ecole) {
    var requete = mvc.get().uri(chemin).with(jwt().jwt(j -> j.subject(utilisateur.toString())));
    if (ecole != null) {
      requete = requete.header("X-Tenant-Id", ecole.toString());
    }
    return requete.exchange();
  }

  private static Map<String, Object> corps(MvcTestResult resultat) throws Exception {
    return new tools.jackson.databind.json.JsonMapper()
        .readValue(resultat.getResponse().getContentAsString(), Map.class);
  }

  @Test
  void sansJetonLApiRepond401() {
    assertThat(mvc.get().uri("/api/v1/moi").header("X-Tenant-Id", ECOLE_A.toString()))
        .hasStatus(401);
  }

  @Test
  void moiRenvoieLesRolesDeLEcoleDemandee() throws Exception {
    MvcTestResult resultat = appel("/api/v1/moi", DIRECTEUR_A, ECOLE_A);
    assertThat(resultat).hasStatusOk();
    assertThat(corps(resultat))
        .containsEntry("utilisateurId", DIRECTEUR_A.toString())
        .containsEntry("ecoleId", ECOLE_A.toString())
        .containsEntry("roles", List.of("DIRECTEUR"));
  }

  @Nested
  class EnseignantDansAParentDansB {

    @Test
    void dansAIlNEstQuEnseignant() throws Exception {
      assertThat(corps(appel("/api/v1/moi", ENSEIGNANT_A_PARENT_B, ECOLE_A)))
          .containsEntry("roles", List.of("ENSEIGNANT"));
      assertThat(appel("/api/essai/enseignant", ENSEIGNANT_A_PARENT_B, ECOLE_A)).hasStatusOk();
      assertThat(appel("/api/essai/parent", ENSEIGNANT_A_PARENT_B, ECOLE_A)).hasStatus(403);
    }

    @Test
    void dansBIlNAQueLesDroitsParent() throws Exception {
      assertThat(corps(appel("/api/v1/moi", ENSEIGNANT_A_PARENT_B, ECOLE_B)))
          .containsEntry("roles", List.of("PARENT"));
      assertThat(appel("/api/essai/parent", ENSEIGNANT_A_PARENT_B, ECOLE_B)).hasStatusOk();
      MvcTestResult refus = appel("/api/essai/enseignant", ENSEIGNANT_A_PARENT_B, ECOLE_B);
      assertThat(refus).hasStatus(403);
      assertThat(corps(refus)).containsEntry("code", "ACCES_REFUSE");
    }
  }

  @Test
  void lesRolesPortesParLeJetonSontIgnores() throws Exception {
    // Même si un jeton prétendait donner DIRECTEUR, seuls les rôles de la table comptent.
    MvcTestResult resultat =
        mvc.get()
            .uri("/api/v1/moi")
            .header("X-Tenant-Id", ECOLE_B.toString())
            .with(
                jwt()
                    .jwt(
                        j ->
                            j.subject(ENSEIGNANT_A_PARENT_B.toString())
                                .claim("realm_access", Map.of("roles", List.of("DIRECTEUR"))))
                    .authorities(new SimpleGrantedAuthority("ROLE_DIRECTEUR")))
            .exchange();
    assertThat(corps(resultat)).containsEntry("roles", List.of("PARENT"));
  }

  @Test
  void lEcoleANeVoitPasLesDonneesDeLEcoleB() throws Exception {
    MvcTestResult dansA = appel("/api/essai/appartenances", DIRECTEUR_A, ECOLE_A);
    assertThat(dansA).hasStatusOk();
    String json = dansA.getResponse().getContentAsString();
    assertThat(json).contains(DIRECTEUR_A.toString()).doesNotContain(SURVEILLANT_B.toString());

    String dansB =
        appel("/api/essai/appartenances", SURVEILLANT_B, ECOLE_B)
            .getResponse()
            .getContentAsString();
    assertThat(dansB).contains(SURVEILLANT_B.toString()).doesNotContain(DIRECTEUR_A.toString());
  }

  @Test
  void unUtilisateurDeANePeutPasDemanderLEcoleB() throws Exception {
    MvcTestResult resultat = appel("/api/essai/appartenances", DIRECTEUR_A, ECOLE_B);
    assertThat(resultat).hasStatus(403);
    assertThat(resultat.getResponse().getContentType())
        .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(corps(resultat)).containsEntry("code", "TENANT_NON_AUTORISE");
  }

  @Test
  void unCheminEncodeNeContournePasLaVerificationDAppartenance() throws Exception {
    // /%61pi = /api une fois décodé (revue sécurité, point bloquant B1).
    MvcTestResult resultat =
        mvc.get()
            .uri(java.net.URI.create("/%61pi/v1/moi"))
            .header("X-Tenant-Id", ECOLE_B.toString())
            .with(jwt().jwt(j -> j.subject(DIRECTEUR_A.toString())))
            .exchange();
    assertThat(resultat).hasStatus(403);
    assertThat(corps(resultat)).containsEntry("code", "TENANT_NON_AUTORISE");
  }

  @Test
  void uneEcoleInconnueEstTraiteeCommeUneEcoleNonAutorisee() throws Exception {
    MvcTestResult resultat = appel("/api/v1/moi", DIRECTEUR_A, UUID.randomUUID());
    assertThat(resultat).hasStatus(403);
    assertThat(corps(resultat)).containsEntry("code", "TENANT_NON_AUTORISE");
  }

  @Test
  void uneEcoleInactiveEstRefusee() throws Exception {
    MvcTestResult resultat = appel("/api/v1/moi", MEMBRE_INACTIVE, ECOLE_INACTIVE);
    assertThat(resultat).hasStatus(403);
    assertThat(corps(resultat)).containsEntry("code", "TENANT_NON_AUTORISE");
  }

  @Test
  void sansEcoleDesigneeLaRequeteEstInvalide() throws Exception {
    MvcTestResult resultat = appel("/api/v1/moi", DIRECTEUR_A, null);
    assertThat(resultat).hasStatus(400);
    assertThat(corps(resultat)).containsEntry("code", "TENANT_ABSENT");
  }

  @Test
  void unEnteteMalFormeEstRefuse() throws Exception {
    MvcTestResult resultat =
        mvc.get()
            .uri("/api/v1/moi")
            .header("X-Tenant-Id", "pas-un-uuid")
            .with(jwt().jwt(j -> j.subject(DIRECTEUR_A.toString())))
            .exchange();
    assertThat(resultat).hasStatus(400);
    assertThat(corps(resultat)).containsEntry("code", "TENANT_INVALIDE");
  }

  @Test
  void unSujetDeJetonQuiNEstPasUnUuidEstRefuse() throws Exception {
    MvcTestResult resultat =
        mvc.get()
            .uri("/api/v1/moi")
            .header("X-Tenant-Id", ECOLE_A.toString())
            .with(jwt().jwt(j -> j.subject("service-account")))
            .exchange();
    assertThat(resultat).hasStatus(401);
    assertThat(corps(resultat)).containsEntry("code", "JETON_INVALIDE");
  }

  @Test
  void lesEntetesDeSecuriteSontPresents() {
    assertThat(appel("/api/v1/moi", DIRECTEUR_A, ECOLE_A))
        .hasHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        .hasHeader("X-Content-Type-Options", "nosniff");
  }

  @Test
  void laSanteEstPubliqueEtLeResteHorsApiEstRefuse() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    assertThat(mvc.get().uri("/actuator/env")).hasStatus(403);
  }
}
