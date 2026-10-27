package sn.ecole.etablissement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import sn.ecole.Fixtures;
import sn.ecole.TestIntegration;
import sn.ecole.shared.StockageMemoire;
import sn.ecole.shared.StockageMemoireConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base des tests d'API du module : les écoles A et B reçoivent le modèle sénégalais avant chaque
 * test, les tables du module sont vidées, et le stockage est en mémoire.
 */
@Import(StockageMemoireConfig.class)
public abstract class TestEtablissement extends TestIntegration {

  protected static final UUID A = Fixtures.ECOLE_A;
  protected static final UUID B = Fixtures.ECOLE_B;

  protected static final JsonMapper JSON = new JsonMapper();

  @Autowired protected MockMvc mockMvc;
  @Autowired protected InitialisationEcole initialisation;
  @Autowired protected StockageMemoire stockage;

  /** Réponse d'API lue : statut, corps JSON et code métier du ProblemDetail. */
  public record Reponse(MockHttpServletResponse brut) {

    public int statut() {
      return brut.getStatus();
    }

    public JsonNode corps() {
      try {
        String texte = brut.getContentAsString();
        return texte.isBlank() ? JSON.createObjectNode() : JSON.readTree(texte);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    public String code() {
      return corps().path("code").asText(null);
    }

    public String etag() {
      return brut.getHeader("ETag");
    }

    public UUID id() {
      return UUID.fromString(corps().path("id").asText());
    }

    public int version() {
      return corps().path("version").asInt(-1);
    }
  }

  @BeforeEach
  void preparerEtablissements() {
    superUtilisateur()
        .execute(
            """
            TRUNCATE ecole.classe_option, ecole.option_enseignement, ecole.parametres_niveau_annee, ecole.parametres_cycle_annee, ecole.classe, ecole.salle, ecole.evenement_calendrier, ecole.periode,
              ecole.annee_scolaire, ecole.niveau_serie, ecole.niveau, ecole.serie, ecole.cycle,
              ecole.etablissement_image, ecole.etablissement CASCADE""");
    stockage.vider();
    initialisation.initialiser(A, "Lycée Exemple A", "LYC-A", StatutEtablissement.PUBLIC);
    initialisation.initialiser(B, "École Exemple B", "ECO-B", StatutEtablissement.PRIVE_LAIQUE);
  }

  // --- Appels ---

  protected Reponse get(UUID utilisateur, UUID ecole, String url) throws Exception {
    return executer(requete(HttpMethod.GET, url), utilisateur, ecole);
  }

  protected Reponse post(UUID utilisateur, UUID ecole, String url, Object corps) throws Exception {
    return executer(avecCorps(HttpMethod.POST, url, corps), utilisateur, ecole);
  }

  protected Reponse put(UUID utilisateur, UUID ecole, String url, int version, Object corps)
      throws Exception {
    return executer(
        avecCorps(HttpMethod.PUT, url, corps).header("If-Match", "\"" + version + "\""),
        utilisateur,
        ecole);
  }

  protected Reponse putSansVersion(UUID utilisateur, UUID ecole, String url, Object corps)
      throws Exception {
    return executer(avecCorps(HttpMethod.PUT, url, corps), utilisateur, ecole);
  }

  protected Reponse delete(UUID utilisateur, UUID ecole, String url) throws Exception {
    return executer(requete(HttpMethod.DELETE, url), utilisateur, ecole);
  }

  protected Reponse televerser(
      UUID utilisateur, UUID ecole, String type, byte[] octets, String nomFichier)
      throws Exception {
    AbstractMockHttpServletRequestBuilder<?> requete =
        MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/etablissement/images/" + type)
            .file(new MockMultipartFile("fichier", nomFichier, "application/octet-stream", octets));
    return executer(requete, utilisateur, ecole);
  }

  private Reponse executer(
      AbstractMockHttpServletRequestBuilder<?> requete, UUID utilisateur, UUID ecole)
      throws Exception {
    AbstractMockHttpServletRequestBuilder<?> complet =
        requete.with(jwt().jwt(j -> j.subject(utilisateur.toString())));
    if (ecole != null) {
      complet.header("X-Tenant-Id", ecole.toString());
    }
    return new Reponse(mockMvc.perform(complet).andReturn().getResponse());
  }

  private static MockHttpServletRequestBuilder requete(HttpMethod methode, String url) {
    return MockMvcRequestBuilders.request(methode, url);
  }

  private static MockHttpServletRequestBuilder avecCorps(
      HttpMethod methode, String url, Object corps) {
    return requete(methode, url)
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(corps));
  }

  // --- Données ---

  /** Corps JSON ordonné. */
  protected static Map<String, Object> corps(Object... clesEtValeurs) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < clesEtValeurs.length; i += 2) {
      m.put((String) clesEtValeurs[i], clesEtValeurs[i + 1]);
    }
    return m;
  }

  protected static byte[] png(int largeur, int hauteur) throws IOException {
    var image =
        new java.awt.image.BufferedImage(
            largeur, hauteur, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream sortie = new ByteArrayOutputStream();
    ImageIO.write(image, "png", sortie);
    return sortie.toByteArray();
  }

  /** Crée une année 2026-2027 en préparation dans l'école, par l'API, et renvoie son id. */
  protected UUID creerAnnee(UUID directeur, UUID ecole) throws Exception {
    Reponse r =
        post(
            directeur,
            ecole,
            "/api/v1/annees-scolaires",
            corps(
                "libelle", "2026-2027",
                "dateDebut", LocalDate.of(2026, 10, 5).toString(),
                "dateFin", LocalDate.of(2027, 7, 2).toString()));
    if (r.statut() != 201) {
      throw new AssertionError("Création de l'année : " + r.statut() + " " + r.corps());
    }
    return r.id();
  }

  protected UUID creerEtOuvrirAnnee(UUID directeur, UUID ecole) throws Exception {
    UUID annee = creerAnnee(directeur, ecole);
    Reponse r = post(directeur, ecole, "/api/v1/annees-scolaires/" + annee + "/ouverture", null);
    if (r.statut() != 200) {
      throw new AssertionError("Ouverture de l'année : " + r.statut() + " " + r.corps());
    }
    return annee;
  }

  /** Id du niveau de code {@code code} (ex. TLE, CP) dans l'école. */
  protected UUID niveau(UUID utilisateur, UUID ecole, String code) throws Exception {
    for (JsonNode n : get(utilisateur, ecole, "/api/v1/niveaux").corps()) {
      if (code.equals(n.path("code").asText())) {
        return UUID.fromString(n.path("id").asText());
      }
    }
    throw new AssertionError("Niveau introuvable : " + code);
  }

  protected UUID serie(UUID utilisateur, UUID ecole, String code) throws Exception {
    for (JsonNode s : get(utilisateur, ecole, "/api/v1/series").corps()) {
      if (code.equals(s.path("code").asText())) {
        return UUID.fromString(s.path("id").asText());
      }
    }
    throw new AssertionError("Série introuvable : " + code);
  }

  protected UUID cycle(UUID utilisateur, UUID ecole, String code) throws Exception {
    for (JsonNode c : get(utilisateur, ecole, "/api/v1/cycles").corps()) {
      if (code.equals(c.path("code").asText())) {
        return UUID.fromString(c.path("id").asText());
      }
    }
    throw new AssertionError("Cycle introuvable : " + code);
  }
}
