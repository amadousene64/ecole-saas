package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.ELEVE_A;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.PARENT_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Profil de l'école et images officielles : droits, validation, isolement. */
class EtablissementApiTest extends TestEtablissement {

  private static final String URL = "/api/v1/etablissement";

  private static Object profil(String nom) {
    return corps(
        "nom", nom,
        "code", "LYC-A",
        "statut", "PRIVE_LAIQUE",
        "ia", "IA Dakar",
        "ief", "IEF Pikine",
        "adresse", "Rue 10, Dakar",
        "telephone", "77 123 45 67",
        "email", "contact@exemple.test");
  }

  @Test
  void sansJetonLaReponseEst401() throws Exception {
    var r =
        new Reponse(
            mockMvc
                .perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(URL)
                        .header("X-Tenant-Id", A.toString()))
                .andReturn()
                .getResponse());
    assertThat(r.statut()).isEqualTo(401);
  }

  @Test
  void tousLesRolesDeLEcoleLisentLeProfil() throws Exception {
    for (UUID utilisateur :
        new UUID[] {
          DIRECTEUR_A, CENSEUR_A, SECRETARIAT_A, ENSEIGNANT_A_PARENT_B, PARENT_A, ELEVE_A
        }) {
      Reponse r = get(utilisateur, A, URL);
      assertThat(r.statut()).as("lecture par %s", utilisateur).isEqualTo(200);
      assertThat(r.corps().path("nom").asText()).isEqualTo("Lycée Exemple A");
      assertThat(r.corps().path("fuseau").asText()).isEqualTo("Africa/Dakar");
      assertThat(r.corps().path("devise").asText()).isEqualTo("XOF");
      assertThat(r.etag()).isNotBlank();
    }
  }

  @Test
  void unUtilisateurQuiNEstPasMembreDeLEcoleEstRefuse() throws Exception {
    Reponse r = get(DIRECTEUR_B, A, URL);
    assertThat(r.statut()).isEqualTo(403);
    assertThat(r.code()).isEqualTo("TENANT_NON_AUTORISE");
  }

  @Test
  void leProfilNonConfigureRepond404() throws Exception {
    superUtilisateur().update("DELETE FROM ecole.etablissement_image");
    superUtilisateur().update("DELETE FROM ecole.etablissement WHERE tenant_id = ?", A);
    Reponse r = get(DIRECTEUR_A, A, URL);
    assertThat(r.statut()).isEqualTo(404);
    assertThat(r.code()).isEqualTo("ETABLISSEMENT_NON_CONFIGURE");
  }

  // --- Écriture ---

  @Test
  void leDirecteurEtLeSecretariatModifientLeProfil() throws Exception {
    Reponse lecture = get(DIRECTEUR_A, A, URL);
    Reponse r = put(DIRECTEUR_A, A, URL, lecture.version(), profil("Lycée Modifié"));
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("nom").asText()).isEqualTo("Lycée Modifié");
    assertThat(r.corps().path("telephone").asText()).isEqualTo("+221771234567");
    assertThat(r.corps().path("statut").asText()).isEqualTo("PRIVE_LAIQUE");
    assertThat(r.version()).isGreaterThan(lecture.version());

    Reponse r2 = put(SECRETARIAT_A, A, URL, r.version(), profil("Lycée Par Le Secrétariat"));
    assertThat(r2.statut()).isEqualTo(200);
  }

  @Test
  void lesAutresRolesNePeuventPasModifierLeProfil() throws Exception {
    int version = get(DIRECTEUR_A, A, URL).version();
    for (UUID utilisateur : new UUID[] {CENSEUR_A, ENSEIGNANT_A_PARENT_B, PARENT_A, ELEVE_A}) {
      Reponse r = put(utilisateur, A, URL, version, profil("Intrus"));
      assertThat(r.statut()).as("écriture par %s", utilisateur).isEqualTo(403);
      assertThat(r.code()).isEqualTo("ACCES_REFUSE");
    }
    assertThat(get(DIRECTEUR_A, A, URL).corps().path("nom").asText()).isEqualTo("Lycée Exemple A");
  }

  @Test
  void uneVersionPerimeeDonne409() throws Exception {
    int version = get(DIRECTEUR_A, A, URL).version();
    assertThat(put(DIRECTEUR_A, A, URL, version, profil("Premier")).statut()).isEqualTo(200);
    Reponse r = put(DIRECTEUR_A, A, URL, version, profil("Second"));
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("CONFLIT_VERSION");
  }

  @Test
  void sansIfMatchLaRequeteEstRefusee() throws Exception {
    Reponse r = putSansVersion(DIRECTEUR_A, A, URL, profil("Sans version"));
    assertThat(r.statut()).isEqualTo(400);
  }

  @Test
  void desDonneesInvalidesDonnent400() throws Exception {
    int version = get(DIRECTEUR_A, A, URL).version();

    Reponse vide =
        put(DIRECTEUR_A, A, URL, version, corps("nom", "", "code", "X", "statut", "PUBLIC"));
    assertThat(vide.statut()).isEqualTo(400);
    assertThat(vide.code()).isEqualTo("VALIDATION");

    Reponse telephone =
        put(
            DIRECTEUR_A,
            A,
            URL,
            version,
            corps("nom", "Lycée", "code", "X", "statut", "PUBLIC", "telephone", "abc"));
    assertThat(telephone.statut()).isEqualTo(400);
    assertThat(telephone.code()).isEqualTo("TELEPHONE_INVALIDE");

    Reponse fuseau =
        put(
            DIRECTEUR_A,
            A,
            URL,
            version,
            corps("nom", "Lycée", "code", "X", "statut", "PUBLIC", "fuseau", "Mars/Olympus"));
    assertThat(fuseau.code()).isEqualTo("FUSEAU_INVALIDE");

    Reponse statut =
        put(DIRECTEUR_A, A, URL, version, corps("nom", "Lycée", "code", "X", "statut", "INCONNU"));
    assertThat(statut.statut()).isEqualTo(400);
  }

  // --- Images ---

  @Test
  void leDirecteurDeposeLeCachetEtSeulLuiEnVoitLUrl() throws Exception {
    Reponse r = televerser(DIRECTEUR_A, A, "CACHET", png(200, 100), "cachet.png");
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("cachetUrl").asText()).startsWith("https://stockage.test/" + A + "/");

    assertThat(get(DIRECTEUR_A, A, URL).corps().path("cachetUrl").isString()).isTrue();
    for (UUID autre : new UUID[] {SECRETARIAT_A, CENSEUR_A, ENSEIGNANT_A_PARENT_B, PARENT_A}) {
      JsonNode corps = get(autre, A, URL).corps();
      assertThat(corps.path("cachetUrl").isNull() || corps.path("cachetUrl").isMissingNode())
          .as("cachet visible par %s", autre)
          .isTrue();
    }
  }

  @Test
  void leSecretariatDeposeLeLogoMaisNiLeCachetNiLaSignature() throws Exception {
    assertThat(televerser(SECRETARIAT_A, A, "LOGO", png(64, 64), "logo.png").statut())
        .isEqualTo(200);
    assertThat(televerser(SECRETARIAT_A, A, "CACHET", png(64, 64), "c.png").statut())
        .isEqualTo(403);
    assertThat(televerser(SECRETARIAT_A, A, "SIGNATURE", png(64, 64), "s.png").statut())
        .isEqualTo(403);
    assertThat(stockage.objets()).hasSize(1);
  }

  @Test
  void lesAutresRolesNeDeposentRien() throws Exception {
    for (UUID utilisateur : new UUID[] {CENSEUR_A, ENSEIGNANT_A_PARENT_B, PARENT_A, ELEVE_A}) {
      assertThat(televerser(utilisateur, A, "LOGO", png(64, 64), "logo.png").statut())
          .as("logo par %s", utilisateur)
          .isEqualTo(403);
    }
    assertThat(stockage.objets()).isEmpty();
  }

  @Test
  void toutLeMondeVoitLeLogo() throws Exception {
    televerser(SECRETARIAT_A, A, "LOGO", png(64, 64), "logo.png");
    for (UUID utilisateur : new UUID[] {PARENT_A, ELEVE_A, CENSEUR_A, ENSEIGNANT_A_PARENT_B}) {
      assertThat(get(utilisateur, A, URL).corps().path("logoUrl").asText())
          .startsWith("https://stockage.test/" + A + "/etablissement/logo/");
    }
  }

  @Test
  void laCleDObjetEstConstruiteParLeServeurDansLeDossierDeLEcole() throws Exception {
    televerser(DIRECTEUR_A, A, "SIGNATURE", png(100, 50), "../../autre-ecole/evil.png");
    assertThat(stockage.objets().keySet())
        .singleElement()
        .asString()
        .startsWith(A + "/etablissement/signature/")
        .doesNotContain("evil")
        .doesNotContain("..");
    String cleEnBase =
        superUtilisateur()
            .queryForObject(
                "SELECT cle FROM ecole.etablissement_image WHERE tenant_id = ? AND type = 'SIGNATURE'",
                String.class,
                A);
    assertThat(stockage.objets()).containsKey(cleEnBase);
  }

  @Test
  void leRemplacementSupprimeLAncienFichier() throws Exception {
    televerser(DIRECTEUR_A, A, "LOGO", png(64, 64), "a.png");
    String ancienne = stockage.objets().keySet().iterator().next();
    televerser(DIRECTEUR_A, A, "LOGO", png(80, 80), "b.png");
    assertThat(stockage.objets()).hasSize(1).doesNotContainKey(ancienne);
  }

  @Test
  void laSuppressionRetireLImageDuStockage() throws Exception {
    televerser(DIRECTEUR_A, A, "LOGO", png(64, 64), "a.png");
    Reponse r = delete(SECRETARIAT_A, A, URL + "/images/LOGO");
    assertThat(r.statut()).isEqualTo(200);
    assertThat(stockage.objets()).isEmpty();
    assertThat(r.corps().path("logoUrl").isNull() || r.corps().path("logoUrl").isMissingNode())
        .isTrue();
    Reponse encore = delete(SECRETARIAT_A, A, URL + "/images/LOGO");
    assertThat(encore.statut()).isEqualTo(404);
    assertThat(encore.code()).isEqualTo("IMAGE_INTROUVABLE");
  }

  @Test
  void leSecretariatNeSupprimePasLeCachet() throws Exception {
    televerser(DIRECTEUR_A, A, "CACHET", png(64, 64), "c.png");
    assertThat(delete(SECRETARIAT_A, A, URL + "/images/CACHET").statut()).isEqualTo(403);
    assertThat(stockage.objets()).hasSize(1);
  }

  @Test
  void lesFichiersNonImagesSontRefuses() throws Exception {
    Reponse svg =
        televerser(
            DIRECTEUR_A,
            A,
            "LOGO",
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8),
            "logo.png");
    assertThat(svg.statut()).isEqualTo(400);
    assertThat(svg.code()).isEqualTo("FICHIER_INVALIDE");

    Reponse html =
        televerser(
            DIRECTEUR_A, A, "LOGO", "<html></html>".getBytes(StandardCharsets.UTF_8), "x.png");
    assertThat(html.code()).isEqualTo("FICHIER_INVALIDE");
    assertThat(stockage.objets()).isEmpty();
  }

  @Test
  void lesImagesTropGrandesSontRefuseesAvantDecodage() throws Exception {
    Reponse grande = televerser(DIRECTEUR_A, A, "LOGO", png(2001, 10), "grande.png");
    assertThat(grande.statut()).isEqualTo(400);
    assertThat(grande.code()).isEqualTo("IMAGE_TROP_GRANDE");

    Reponse bombe = televerser(DIRECTEUR_A, A, "LOGO", pngAnnoncant(40_000, 40_000), "bombe.png");
    assertThat(bombe.statut()).isEqualTo(400);
    assertThat(bombe.code()).isEqualTo("IMAGE_TROP_GRANDE");
    assertThat(stockage.objets()).isEmpty();
  }

  @Test
  void unFichierTropVolumineuxPourSonTypeEstRefuse() throws Exception {
    byte[] gros = new byte[600_000];
    Reponse r = televerser(DIRECTEUR_A, A, "SIGNATURE", gros, "s.png");
    assertThat(r.statut()).isEqualTo(400);
    assertThat(r.code()).isEqualTo("FICHIER_TROP_VOLUMINEUX");
  }

  @Test
  void unTypeDImageInconnuEstRefuse() throws Exception {
    assertThat(televerser(DIRECTEUR_A, A, "BANNIERE", png(10, 10), "b.png").statut())
        .isEqualTo(400);
  }

  // --- Isolement ---

  @Test
  void lesImagesEtLeProfilDesEcolesSontSepares() throws Exception {
    televerser(DIRECTEUR_A, A, "LOGO", png(64, 64), "a.png");
    televerser(DIRECTEUR_B, B, "LOGO", png(64, 64), "b.png");
    Reponse a = get(DIRECTEUR_A, A, URL);
    Reponse b = get(DIRECTEUR_B, B, URL);
    assertThat(a.corps().path("nom").asText()).isEqualTo("Lycée Exemple A");
    assertThat(b.corps().path("nom").asText()).isEqualTo("École Exemple B");
    assertThat(a.corps().path("logoUrl").asText())
        .contains("/" + A + "/")
        .doesNotContain(B.toString());
    assertThat(b.corps().path("logoUrl").asText())
        .contains("/" + B + "/")
        .doesNotContain(A.toString());

    // Modifier B ne touche pas A.
    put(DIRECTEUR_B, B, URL, b.version(), profil("B modifiée"));
    assertThat(get(DIRECTEUR_A, A, URL).corps().path("nom").asText()).isEqualTo("Lycée Exemple A");
    // Le directeur de B ne peut ni lire ni modifier A en désignant A.
    assertThat(get(DIRECTEUR_B, A, URL).statut()).isEqualTo(403);
    assertThat(put(DIRECTEUR_B, A, URL, a.version(), profil("Intrusion")).statut()).isEqualTo(403);
    assertThat(televerser(DIRECTEUR_B, A, "LOGO", png(10, 10), "x.png").statut()).isEqualTo(403);
  }

  // --- Aide : PNG dont l'en-tête annonce de grandes dimensions ---

  private static byte[] pngAnnoncant(int largeur, int hauteur) throws IOException {
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
    ihdr.write(entier(largeur));
    ihdr.write(entier(hauteur));
    ihdr.write(new byte[] {8, 2, 0, 0, 0});
    bloc(png, "IHDR", ihdr.toByteArray());
    Deflater deflater = new Deflater();
    deflater.setInput(new byte[] {0});
    deflater.finish();
    byte[] compresse = new byte[32];
    int n = deflater.deflate(compresse);
    bloc(png, "IDAT", java.util.Arrays.copyOf(compresse, n));
    bloc(png, "IEND", new byte[0]);
    return png.toByteArray();
  }

  private static void bloc(ByteArrayOutputStream sortie, String type, byte[] donnees)
      throws IOException {
    byte[] nom = type.getBytes(StandardCharsets.US_ASCII);
    sortie.write(entier(donnees.length));
    sortie.write(nom);
    sortie.write(donnees);
    CRC32 crc = new CRC32();
    crc.update(nom);
    crc.update(donnees);
    sortie.write(entier((int) crc.getValue()));
  }

  private static byte[] entier(int v) {
    return new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
  }
}
