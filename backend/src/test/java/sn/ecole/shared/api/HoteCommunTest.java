package sn.ecole.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import sn.ecole.TestIntegration;

/**
 * Mode sous-domaine (production) avec un hôte commun déclaré pour l'app parent : sur cet hôte
 * l'école vient de X-Tenant-Id, toujours contrôlée par les appartenances ; sur le sous-domaine
 * d'une école, le mode reste strict.
 */
@TestPropertySource(
    properties = {
      "ecole.tenant.resolution=sous-domaine",
      "ecole.tenant.domaine-racine=monapp.sn",
      "ecole.tenant.hotes-communs=parent.monapp.sn"
    })
class HoteCommunTest extends TestIntegration {

  @Autowired MockMvcTester mvc;

  private MvcTestResult appel(String hote, UUID utilisateur, UUID ecole) {
    var requete =
        mvc.get()
            .uri("/api/v1/moi")
            .with(
                r -> {
                  r.setServerName(hote);
                  return r;
                })
            .with(jwt().jwt(j -> j.subject(utilisateur.toString())));
    if (ecole != null) {
      requete = requete.header("X-Tenant-Id", ecole.toString());
    }
    return requete.exchange();
  }

  @Test
  void surLHoteCommunLUtilisateurChoisitParmiSesEcoles() {
    // Parent en B, enseignant en A : chaque école désignée donne SES rôles.
    MvcTestResult b = appel("parent.monapp.sn", ENSEIGNANT_A_PARENT_B, ECOLE_B);
    assertThat(b).hasStatus(200);
    assertThat(b).bodyJson().extractingPath("$.roles").asList().containsExactly("PARENT");
    MvcTestResult a = appel("parent.monapp.sn", ENSEIGNANT_A_PARENT_B, ECOLE_A);
    assertThat(a).hasStatus(200);
    assertThat(a).bodyJson().extractingPath("$.roles").asList().containsExactly("ENSEIGNANT");
  }

  @Test
  void surLHoteCommunUneEcoleDontOnNEstPasMembreEstRefusee() {
    MvcTestResult refus = appel("parent.monapp.sn", DIRECTEUR_A, ECOLE_B);
    assertThat(refus).hasStatus(403);
    assertThat(refus).bodyJson().extractingPath("$.code").isEqualTo("TENANT_NON_AUTORISE");
  }

  @Test
  void surLHoteCommunSansEcoleDesigneeLaRequeteEstInvalide() {
    assertThat(appel("parent.monapp.sn", DIRECTEUR_A, null)).hasStatus(400);
  }

  @Test
  void surLeSousDomaineDUneEcoleLeModeResteStrict() {
    // Même utilisateur, même école visée par l'en-tête, mais depuis le sous-domaine d'une autre.
    MvcTestResult incoherent = appel("lycee-a.monapp.sn", ENSEIGNANT_A_PARENT_B, ECOLE_B);
    assertThat(incoherent).hasStatus(400);
    assertThat(incoherent).bodyJson().extractingPath("$.code").isEqualTo("TENANT_INCOHERENT");
    // Le sous-domaine seul suffit, comme avant.
    assertThat(appel("lycee-a.monapp.sn", DIRECTEUR_A, null)).hasStatus(200);
  }
}
