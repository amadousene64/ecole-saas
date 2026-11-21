package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.COMPTABLE_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.SECRETARIAT_A;
import static sn.ecole.Fixtures.SURVEILLANT_A;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Assistant d'inscription : brouillon chiffré enregistré à chaque étape, pièces rattachées au
 * brouillon, finalisation tout ou rien, isolement des écoles et droits.
 */
class BrouillonsInscriptionApiTest extends TestInscription {

  private static final String BROUILLONS = "/api/v1/brouillons-inscription";

  private UUID classe;

  @BeforeEach
  void structure() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    Reponse r =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/classes",
            corps(
                "anneeId",
                annee,
                "niveauId",
                niveau(DIRECTEUR_A, A, "CI"),
                "nom",
                "CI A",
                "capacite",
                30));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    classe = r.id();
  }

  private Reponse ouvrir(UUID qui, UUID ecole) throws Exception {
    return post(qui, ecole, BROUILLONS, corps());
  }

  private UUID ouvert() throws Exception {
    Reponse r = ouvrir(SECRETARIAT_A, A);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  private static Map<String, Object> eleve() {
    return identite("Awa", "Fictif");
  }

  private static Map<String, Object> nouveauResponsable(String prenoms, boolean legal) {
    return corps(
        "prenoms",
        prenoms,
        "nom",
        "Parent-Fictif",
        "telephone",
        "770000077",
        "type",
        "MERE",
        "responsableLegal",
        legal,
        "payeur",
        true,
        "contactUrgence",
        true,
        "autoriseRecuperer",
        true,
        "recoitNotifications",
        true);
  }

  private Map<String, Object> complet(List<Map<String, Object>> responsables) {
    return corps(
        "eleve",
        eleve(),
        "responsables",
        responsables,
        "scolarite",
        corps("classeId", classe, "options", List.of(), "redoublant", false));
  }

  private Reponse enregistrer(UUID qui, UUID id, int version, int etape, Object contenu)
      throws Exception {
    return put(qui, A, BROUILLONS + "/" + id, version, corps("etape", etape, "contenu", contenu));
  }

  private Reponse finaliser(UUID qui, UUID ecole, UUID id, int version) throws Exception {
    return executer(
        MockMvcRequestBuilders.post(BROUILLONS + "/" + id + "/finalisation")
            .header("If-Match", "\"" + version + "\"")
            .contentType("application/json")
            .content(JSON.writeValueAsString(corps("statut", "INSCRIT"))),
        qui,
        ecole);
  }

  private Reponse deposerPiece(UUID id, String texte) throws Exception {
    return executer(
        MockMvcRequestBuilders.multipart(HttpMethod.POST, BROUILLONS + "/" + id + "/pieces")
            .file(
                new MockMultipartFile(
                    "fichier",
                    "acte.pdf",
                    "application/pdf",
                    ("%PDF-1.4\n% document fictif\n" + texte + "\n%%EOF")
                        .getBytes(StandardCharsets.US_ASCII)))
            .file(new MockMultipartFile("type", "", "text/plain", "ACTE_NAISSANCE".getBytes())),
        SECRETARIAT_A,
        A);
  }

  private int nombre(String table) {
    Integer n =
        superUtilisateur()
            .queryForObject(
                "SELECT count(*) FROM ecole." + table + " WHERE tenant_id = ?", Integer.class, A);
    return n == null ? 0 : n;
  }

  @Test
  void leBrouillonSEnregistreEtapeParEtapeEtSeReprend() throws Exception {
    Reponse ouverture = ouvrir(SECRETARIAT_A, A);
    assertThat(ouverture.statut()).isEqualTo(201);
    assertThat(ouverture.corps().path("etape").asInt()).isEqualTo(1);
    UUID id = ouverture.id();

    Reponse etape2 =
        enregistrer(SECRETARIAT_A, id, versionEtag(ouverture), 2, corps("eleve", eleve()));
    assertThat(etape2.statut()).as(etape2.corps().toString()).isEqualTo(200);
    assertThat(versionEtag(etape2)).isGreaterThan(versionEtag(ouverture));

    // Un autre membre de l'équipe reprend le brouillon où il a été laissé.
    Reponse lu = get(DIRECTEUR_A, A, BROUILLONS + "/" + id);
    assertThat(lu.statut()).isEqualTo(200);
    assertThat(lu.corps().path("etape").asInt()).isEqualTo(2);
    assertThat(lu.corps().path("contenu").path("eleve").path("prenoms").asText()).isEqualTo("Awa");

    Reponse liste = get(SECRETARIAT_A, A, BROUILLONS);
    assertThat(liste.corps()).hasSize(1);
    assertThat(liste.corps().get(0).path("libelle").asText()).isEqualTo("Awa Fictif");
  }

  @Test
  void leContenuEstChiffreEnBase() throws Exception {
    UUID id = ouvert();
    enregistrer(SECRETARIAT_A, id, 0, 2, corps("eleve", eleve()));
    byte[] brut =
        superUtilisateur()
            .queryForObject(
                "SELECT contenu FROM ecole.brouillon_inscription WHERE id = ?", byte[].class, id);
    assertThat(brut).isNotEmpty();
    String lisible = new String(brut, StandardCharsets.ISO_8859_1);
    assertThat(lisible).doesNotContain("Awa").doesNotContain("Fictif").doesNotContain("prenoms");
    // Rien de lisible au journal non plus.
    assertThat(journal("BROUILLON_OUVERT")).singleElement();
    assertThat(journal("BROUILLON_OUVERT").toString()).doesNotContain("Awa");
  }

  @Test
  void uneVersionPerimeeEstRefusee() throws Exception {
    UUID id = ouvert();
    assertThat(enregistrer(SECRETARIAT_A, id, 0, 2, corps("eleve", eleve())).statut())
        .isEqualTo(200);
    Reponse perimee = enregistrer(DIRECTEUR_A, id, 0, 3, corps("eleve", eleve()));
    assertThat(perimee.statut()).isEqualTo(409);
    assertThat(perimee.code()).isEqualTo("CONFLIT_VERSION");
  }

  @Test
  void laFinalisationCreeEleveResponsablesInscriptionEtRattacheLesPieces() throws Exception {
    UUID existant = creerResponsable(SECRETARIAT_A, A, "Tante", "770000009");
    UUID id = ouvert();
    Reponse piece = deposerPiece(id, "acte fictif");
    assertThat(piece.statut()).as(piece.corps().toString()).isEqualTo(201);
    assertThat(get(SECRETARIAT_A, A, BROUILLONS + "/" + id + "/pieces").corps()).hasSize(1);

    Map<String, Object> tante =
        corps(
            "responsableId",
            existant,
            "type",
            "TUTEUR",
            "responsableLegal",
            false,
            "payeur",
            false,
            "contactUrgence",
            true,
            "autoriseRecuperer",
            true,
            "recoitNotifications",
            false);
    Reponse etape5 =
        enregistrer(
            SECRETARIAT_A, id, 0, 5, complet(List.of(nouveauResponsable("Mère", true), tante)));
    assertThat(etape5.statut()).as(etape5.corps().toString()).isEqualTo(200);

    Reponse fin = finaliser(SECRETARIAT_A, A, id, versionEtag(etape5));
    assertThat(fin.statut()).as(fin.corps().toString()).isEqualTo(201);
    UUID eleve = UUID.fromString(fin.corps().path("eleveId").asText());

    assertThat(get(SECRETARIAT_A, A, ELEVES + "/" + eleve).corps().path("prenoms").asText())
        .isEqualTo("Awa");
    assertThat(get(SECRETARIAT_A, A, ELEVES + "/" + eleve + "/responsables").corps()).hasSize(2);
    assertThat(get(SECRETARIAT_A, A, ELEVES + "/" + eleve + "/inscriptions").corps())
        .singleElement()
        .satisfies(i -> assertThat(i.path("statut").asText()).isEqualTo("INSCRIT"));
    assertThat(get(SECRETARIAT_A, A, ELEVES + "/" + eleve + "/pieces").corps()).hasSize(1);

    // Brouillon clos : introuvable, contenu effacé en base.
    assertThat(get(SECRETARIAT_A, A, BROUILLONS + "/" + id).statut()).isEqualTo(404);
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT contenu IS NULL AND supprime_le IS NOT NULL"
                        + " FROM ecole.brouillon_inscription WHERE id = ?",
                    Boolean.class,
                    id))
        .isTrue();
    // Une seconde finalisation ne crée rien.
    assertThat(finaliser(SECRETARIAT_A, A, id, versionEtag(etape5)).statut()).isEqualTo(404);
    assertThat(nombre("eleve")).isEqualTo(1);
  }

  @Test
  void unBrouillonIncompletNeSeFinalisePas() throws Exception {
    UUID id = ouvert();
    Reponse sansResponsableLegal =
        enregistrer(SECRETARIAT_A, id, 0, 5, complet(List.of(nouveauResponsable("Tante", false))));
    Reponse fin = finaliser(SECRETARIAT_A, A, id, versionEtag(sansResponsableLegal));
    assertThat(fin.statut()).isEqualTo(400);
    assertThat(fin.code()).isEqualTo("BROUILLON_INCOMPLET");
    assertThat(fin.corps().path("detail").asText()).contains("responsable légal");
    assertThat(nombre("eleve")).isZero();
  }

  @Test
  void uneErreurALaFinalisationNeCreeRienEtGardeLeBrouillon() throws Exception {
    UUID id = ouvert();
    Map<String, Object> contenu = complet(List.of(nouveauResponsable("Mère", true)));
    contenu.put("scolarite", corps("classeId", UUID.randomUUID(), "redoublant", false));
    Reponse etape5 = enregistrer(SECRETARIAT_A, id, 0, 5, contenu);
    Reponse fin = finaliser(SECRETARIAT_A, A, id, versionEtag(etape5));
    assertThat(fin.statut()).isBetween(400, 499);
    assertThat(nombre("eleve")).isZero();
    assertThat(nombre("responsable")).isZero();
    assertThat(get(SECRETARIAT_A, A, BROUILLONS + "/" + id).statut()).isEqualTo(200);
  }

  @Test
  void lAbandonEffaceLeContenuEtLesPieces() throws Exception {
    UUID id = ouvert();
    deposerPiece(id, "acte fictif");
    assertThat(stockage.objets()).hasSize(1);
    Reponse etape = enregistrer(SECRETARIAT_A, id, 0, 2, corps("eleve", eleve()));
    Reponse abandon =
        executer(
            MockMvcRequestBuilders.delete(BROUILLONS + "/" + id)
                .header("If-Match", "\"" + versionEtag(etape) + "\""),
            SECRETARIAT_A,
            A);
    assertThat(abandon.statut()).isEqualTo(204);
    assertThat(stockage.objets()).isEmpty();
    assertThat(get(SECRETARIAT_A, A, BROUILLONS).corps()).isEmpty();
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT contenu IS NULL FROM ecole.brouillon_inscription WHERE id = ?",
                    Boolean.class,
                    id))
        .isTrue();
  }

  @Test
  void lEcoleBNeVoitNiNeModifieLesBrouillonsDeLEcoleA() throws Exception {
    UUID id = ouvert();
    enregistrer(SECRETARIAT_A, id, 0, 2, corps("eleve", eleve()));
    assertThat(get(DIRECTEUR_B, B, BROUILLONS + "/" + id).statut()).isEqualTo(404);
    assertThat(get(DIRECTEUR_B, B, BROUILLONS).corps()).isEmpty();
    assertThat(
            put(DIRECTEUR_B, B, BROUILLONS + "/" + id, 1, corps("etape", 3, "contenu", corps()))
                .statut())
        .isEqualTo(404);
    assertThat(finaliser(DIRECTEUR_B, B, id, 1).statut()).isEqualTo(404);
    assertThat(get(DIRECTEUR_B, B, BROUILLONS + "/" + id + "/pieces").statut()).isEqualTo(404);
  }

  @Test
  void seulsLaDirectionEtLeSecretariatUtilisentLAssistant() throws Exception {
    UUID id = ouvert();
    for (UUID qui : List.of(CENSEUR_A, COMPTABLE_A, SURVEILLANT_A)) {
      assertThat(ouvrir(qui, A).statut()).isEqualTo(403);
      assertThat(get(qui, A, BROUILLONS).statut()).isEqualTo(403);
      assertThat(get(qui, A, BROUILLONS + "/" + id).statut()).isEqualTo(403);
      assertThat(finaliser(qui, A, id, 0).statut()).isEqualTo(403);
    }
    assertThat(ouvrir(DIRECTEUR_A, A).statut()).isEqualTo(201);
  }
}
