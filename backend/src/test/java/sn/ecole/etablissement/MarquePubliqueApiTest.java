package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Marque publique d'une école (pages de connexion) : sans jeton, mais uniquement ce qui est public.
 * Le logo seulement : jamais le cachet ni la signature. Isolement entre écoles, CORS limité à
 * Keycloak, débit limité.
 */
class MarquePubliqueApiTest extends TestEtablissement {

  private static final String MARQUE = "/api/v1/public/marque";

  /** GET sans aucun jeton ni en-tête d'école, comme le fait le navigateur depuis Keycloak. */
  private MockHttpServletResponse publique(String url) throws Exception {
    return mockMvc.perform(MockMvcRequestBuilders.get(url)).andReturn().getResponse();
  }

  private Reponse profil(UUID directeur, UUID ecole, String ville, String phrase) throws Exception {
    Reponse lu = get(directeur, ecole, "/api/v1/etablissement");
    var corps = lu.corps();
    return put(
        directeur,
        ecole,
        "/api/v1/etablissement",
        corps.path("version").asInt(),
        corps(
            "nom", corps.path("nom").asText(),
            "code", corps.path("code").asText(),
            "statut", corps.path("statut").asText(),
            "ville", ville,
            "phraseAccueil", phrase));
  }

  private static BufferedImage image(byte[] octets) throws Exception {
    return ImageIO.read(new ByteArrayInputStream(octets));
  }

  @Test
  void sansEcoleDesigneeLaMarqueEstNeutreEtJamaisUneErreur() throws Exception {
    MockHttpServletResponse r = publique(MARQUE);

    assertThat(r.getStatus()).isEqualTo(200);
    String json = r.getContentAsString();
    assertThat(JSON.readTree(json).path("nom").isNull()).isTrue();
    assertThat(JSON.readTree(json).path("logoVersion").isNull()).isTrue();
    assertThat(r.getHeader("Cache-Control")).contains("public").contains("max-age=300");
    // Une école inconnue donne exactement la même réponse : aucune énumération des écoles.
    assertThat(publique(MARQUE + "?ecole=" + UUID.randomUUID()).getContentAsString())
        .isEqualTo(json);
  }

  @Test
  void lEcoleDesigneeMontreSonNomSaVilleEtSaPhraseSansAucunJeton() throws Exception {
    assertThat(profil(DIRECTEUR_A, A, "Dakar", "Apprendre ensemble.").statut()).isEqualTo(200);

    MockHttpServletResponse r = publique(MARQUE + "?ecole=" + A);

    assertThat(r.getStatus()).isEqualTo(200);
    var corps = JSON.readTree(r.getContentAsString());
    assertThat(corps.path("nom").asText()).isEqualTo("Lycée Exemple A");
    assertThat(corps.path("ville").asText()).isEqualTo("Dakar");
    assertThat(corps.path("phraseAccueil").asText()).isEqualTo("Apprendre ensemble.");
    assertThat(corps.path("logoVersion").isNull()).isTrue();
    // Rien d'autre que ces quatre champs.
    assertThat(corps.size()).isEqualTo(4);
  }

  @Test
  void lEcoleBNeVoitJamaisLaMarqueDeLEcoleA() throws Exception {
    profil(DIRECTEUR_A, A, "Dakar", "Phrase de A.");
    profil(DIRECTEUR_B, B, "Thiès", "Phrase de B.");

    var a = JSON.readTree(publique(MARQUE + "?ecole=" + A).getContentAsString());
    var b = JSON.readTree(publique(MARQUE + "?ecole=" + B).getContentAsString());

    assertThat(a.path("nom").asText()).isEqualTo("Lycée Exemple A");
    assertThat(a.path("ville").asText()).isEqualTo("Dakar");
    assertThat(b.path("nom").asText()).isEqualTo("École Exemple B");
    assertThat(b.path("ville").asText()).isEqualTo("Thiès");
    assertThat(b.toString()).doesNotContain("Phrase de A").doesNotContain("Lycée Exemple A");
  }

  @Test
  void uneEcoleInactiveDonneLaMarqueNeutre() throws Exception {
    var corps =
        JSON.readTree(
            publique(MARQUE + "?ecole=" + sn.ecole.Fixtures.ECOLE_INACTIVE).getContentAsString());
    assertThat(corps.path("nom").isNull()).isTrue();
  }

  @Test
  void laMarqueSuitLesModificationsDuProfilSansAttendreLExpirationDuCache() throws Exception {
    profil(DIRECTEUR_A, A, "Dakar", "Première phrase.");
    assertThat(
            JSON.readTree(publique(MARQUE + "?ecole=" + A).getContentAsString())
                .path("phraseAccueil")
                .asText())
        .isEqualTo("Première phrase.");

    profil(DIRECTEUR_A, A, "Dakar", "Seconde phrase.");

    assertThat(
            JSON.readTree(publique(MARQUE + "?ecole=" + A).getContentAsString())
                .path("phraseAccueil")
                .asText())
        .isEqualTo("Seconde phrase.");
  }

  @Test
  void seulLeLogoEstPublicJamaisLeCachetNiLaSignature() throws Exception {
    // Trois images de dimensions différentes pour savoir laquelle sort.
    assertThat(televerser(DIRECTEUR_A, A, "LOGO", png(64, 32), "logo.png").statut()).isEqualTo(200);
    assertThat(televerser(DIRECTEUR_A, A, "CACHET", png(200, 100), "cachet.png").statut())
        .isEqualTo(200);
    assertThat(televerser(DIRECTEUR_A, A, "SIGNATURE", png(120, 60), "signature.png").statut())
        .isEqualTo(200);

    MockHttpServletResponse marque = publique(MARQUE + "?ecole=" + A);
    var corps = JSON.readTree(marque.getContentAsString());
    assertThat(corps.path("logoVersion").asText()).isNotBlank();
    // La réponse ne contient aucune adresse, ni mot évoquant le cachet ou la signature.
    assertThat(marque.getContentAsString().toLowerCase())
        .doesNotContain("cachet")
        .doesNotContain("signature")
        .doesNotContain("http");

    MockHttpServletResponse logo = publique(MARQUE + "/logo?ecole=" + A);
    assertThat(logo.getStatus()).isEqualTo(200);
    assertThat(logo.getContentType()).isEqualTo("image/png");
    BufferedImage lu = image(logo.getContentAsByteArray());
    assertThat(lu.getWidth()).isEqualTo(64);
    assertThat(lu.getHeight()).isEqualTo(32);
    assertThat(logo.getHeader("Cache-Control")).contains("public").contains("max-age=300");
    assertThat(logo.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(logo.getHeader("Content-Security-Policy")).contains("default-src 'none'");

    // Aucun paramètre ne permet de demander une autre image : le type n'existe pas ici.
    for (String type : new String[] {"CACHET", "SIGNATURE"}) {
      MockHttpServletResponse detourne = publique(MARQUE + "/logo?ecole=" + A + "&type=" + type);
      assertThat(image(detourne.getContentAsByteArray()).getWidth()).isEqualTo(64);
      assertThat(publique(MARQUE + "/images/" + type + "?ecole=" + A).getStatus()).isEqualTo(404);
      assertThat(publique(MARQUE + "/" + type.toLowerCase() + "?ecole=" + A).getStatus())
          .isEqualTo(404);
    }
  }

  @Test
  void leCachetDeposeSeulNeSortJamaisDuPublic() throws Exception {
    televerser(DIRECTEUR_A, A, "CACHET", png(200, 100), "cachet.png");
    televerser(DIRECTEUR_A, A, "SIGNATURE", png(120, 60), "signature.png");

    assertThat(publique(MARQUE + "/logo?ecole=" + A).getStatus()).isEqualTo(404);
    assertThat(
            JSON.readTree(publique(MARQUE + "?ecole=" + A).getContentAsString())
                .path("logoVersion")
                .isNull())
        .isTrue();
  }

  @Test
  void leLogoNeSortQueDeSonEcoleEtSeValideParETag() throws Exception {
    televerser(DIRECTEUR_A, A, "LOGO", png(64, 32), "logo.png");
    televerser(DIRECTEUR_B, B, "LOGO", png(10, 10), "logo.png");

    assertThat(image(publique(MARQUE + "/logo?ecole=" + A).getContentAsByteArray()).getWidth())
        .isEqualTo(64);
    assertThat(image(publique(MARQUE + "/logo?ecole=" + B).getContentAsByteArray()).getWidth())
        .isEqualTo(10);

    MockHttpServletResponse premiere = publique(MARQUE + "/logo?ecole=" + A);
    String etag = premiere.getHeader("ETag");
    assertThat(etag).isNotBlank();
    MockHttpServletResponse conditionnelle =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(MARQUE + "/logo?ecole=" + A)
                    .header("If-None-Match", etag))
            .andReturn()
            .getResponse();
    assertThat(conditionnelle.getStatus()).isEqualTo(304);

    // Un nouveau logo change la version : l'ancienne copie n'est plus valable.
    televerser(SECRETARIAT_A, A, "LOGO", png(48, 24), "logo2.png");
    assertThat(publique(MARQUE + "/logo?ecole=" + A).getHeader("ETag")).isNotEqualTo(etag);
  }

  @Test
  void ceNEstQueDeLaLectureEtJamaisUneEcriture() throws Exception {
    for (var requete :
        new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
          MockMvcRequestBuilders.post(MARQUE),
          MockMvcRequestBuilders.put(MARQUE),
          MockMvcRequestBuilders.delete(MARQUE)
        }) {
      int statut = mockMvc.perform(requete).andReturn().getResponse().getStatus();
      assertThat(statut).isIn(401, 403);
    }
  }

  @Test
  void leCorsNeCouvreQueLOrigineDeKeycloak() throws Exception {
    MockHttpServletResponse permise =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(MARQUE + "?ecole=" + A)
                    .header("Origin", "http://keycloak.test:8180"))
            .andReturn()
            .getResponse();
    assertThat(permise.getHeader("Access-Control-Allow-Origin"))
        .isEqualTo("http://keycloak.test:8180");
    assertThat(permise.getHeader("Access-Control-Allow-Credentials")).isNull();

    MockHttpServletResponse autre =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(MARQUE + "?ecole=" + A)
                    .header("Origin", "https://pirate.example"))
            .andReturn()
            .getResponse();
    assertThat(autre.getHeader("Access-Control-Allow-Origin")).isNull();
    assertThat(autre.getStatus()).isEqualTo(403);

    MockHttpServletResponse preverification =
        mockMvc
            .perform(
                MockMvcRequestBuilders.options(MARQUE)
                    .header("Origin", "http://keycloak.test:8180")
                    .header("Access-Control-Request-Method", "GET"))
            .andReturn()
            .getResponse();
    assertThat(preverification.getStatus()).isEqualTo(200);
    assertThat(preverification.getHeader("Access-Control-Allow-Methods")).contains("GET");
    assertThat(preverification.getHeader("Access-Control-Allow-Methods")).doesNotContain("POST");
  }

  @Test
  void lesAppelsSontLimitesParAdresse() throws Exception {
    int refus = 0;
    for (int i = 0; i < 125; i++) {
      MockHttpServletResponse r = publique(MARQUE);
      if (r.getStatus() == 429) {
        refus++;
        assertThat(JSON.readTree(r.getContentAsString()).path("code").asText())
            .isEqualTo("TROP_DE_TENTATIVES");
      }
    }
    assertThat(refus).isGreaterThanOrEqualTo(4);
  }
}
