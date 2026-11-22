package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.COMPTABLE_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.ELEVE_A;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.PARENT_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;
import static sn.ecole.Fixtures.SURVEILLANT_A;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Matières (modèle sénégalais, saisie, droits) et grille horaire des cycles. */
class MatieresEtCreneauxApiTest extends TestEtablissement {

  private static final String MATIERES = "/api/v1/matieres";
  private static final List<String> SEMAINE =
      List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");

  private static Object matiere(String code, String libelle, List<String> cycles) {
    return corps("code", code, "libelle", libelle, "cycles", cycles, "ordre", 500);
  }

  private static Object creneau(String debut, String fin, List<String> jours) {
    return corps("debut", debut, "fin", fin, "jours", jours);
  }

  private static int version(Reponse r) {
    return r.corps().path("version").asInt();
  }

  private List<String> codes(UUID qui, UUID ecole) throws Exception {
    List<String> codes = new ArrayList<>();
    get(qui, ecole, MATIERES).corps().forEach(m -> codes.add(m.path("code").asText()));
    return codes;
  }

  private JsonNode matiereParCode(String code) throws Exception {
    for (JsonNode m : get(DIRECTEUR_A, A, MATIERES).corps()) {
      if (code.equals(m.path("code").asText())) {
        return m;
      }
    }
    throw new AssertionError("Matière introuvable : " + code);
  }

  // --- Matières ---

  @Test
  void chaqueEcoleRecoitLesMatieresUsuellesDansLOrdre() throws Exception {
    List<String> codes = codes(DIRECTEUR_A, A);
    assertThat(codes).hasSize(17).startsWith("LC", "MATHS", "DM").endsWith("EPS");
    JsonNode maths = matiereParCode("MATHS");
    assertThat(maths.path("cycles").toString())
        .isEqualTo("[\"ELEMENTAIRE\",\"MOYEN\",\"SECONDAIRE\"]");
    assertThat(matiereParCode("ESP").path("groupe").asText()).isEqualTo("Langues vivantes 2");
    assertThat(codes(DIRECTEUR_B, B)).hasSize(17);
  }

  @Test
  void leModeleNAjouteQueLesMatieresManquantes() throws Exception {
    assertThat(post(DIRECTEUR_A, A, MATIERES + "/modele", null).corps()).isEmpty();
    String maths = matiereParCode("MATHS").path("id").asText();
    assertThat(delete(DIRECTEUR_A, A, MATIERES + "/" + maths).statut()).isEqualTo(204);
    assertThat(codes(DIRECTEUR_A, A)).doesNotContain("MATHS");

    JsonNode ajoutees = post(CENSEUR_A, A, MATIERES + "/modele", null).corps();
    assertThat(ajoutees).hasSize(1);
    assertThat(ajoutees.get(0).path("code").asText()).isEqualTo("MATHS");
    assertThat(codes(DIRECTEUR_A, A)).hasSize(17);
  }

  @Test
  void uneMatiereSeCreeSeModifieEtSeSupprime() throws Exception {
    Reponse cree =
        post(
            CENSEUR_A,
            A,
            MATIERES,
            corps(
                "code",
                "dissert",
                "libelle",
                "Dissertation",
                "groupe",
                "Français",
                "cycles",
                List.of("SECONDAIRE", "MOYEN"),
                "ordre",
                61));
    assertThat(cree.statut()).as(cree.corps().toString()).isEqualTo(201);
    assertThat(cree.corps().path("code").asText()).isEqualTo("DISSERT");
    // Ordre des cycles : celui du référentiel, pas celui reçu.
    assertThat(cree.corps().path("cycles").toString()).isEqualTo("[\"MOYEN\",\"SECONDAIRE\"]");

    Reponse doublon = post(SECRETARIAT_A, A, MATIERES, matiere("DISSERT", "Autre", SEMAINE_CYCLES));
    assertThat(doublon.statut()).isEqualTo(409);
    assertThat(doublon.code()).isEqualTo("MATIERE_EXISTANTE");
    assertThat(post(DIRECTEUR_A, A, MATIERES, matiere("SANS", "Sans cycle", List.of())).statut())
        .isEqualTo(400);

    String url = MATIERES + "/" + cree.id();
    Reponse modifiee =
        put(
            DIRECTEUR_A,
            A,
            url,
            version(cree),
            corps(
                "code",
                "DISSERT",
                "libelle",
                "Dissertation française",
                "cycles",
                List.of("SECONDAIRE"),
                "ordre",
                61,
                "actif",
                false));
    assertThat(modifiee.statut()).isEqualTo(200);
    assertThat(modifiee.corps().path("actif").asBoolean()).isFalse();
    assertThat(modifiee.corps().path("groupe").isNull()).isTrue();
    assertThat(
            put(DIRECTEUR_A, A, url, version(cree), matiere("DISSERT", "X", SEMAINE_CYCLES)).code())
        .isEqualTo("CONFLIT_VERSION");

    assertThat(delete(DIRECTEUR_A, A, url).statut()).isEqualTo(204);
    assertThat(codes(DIRECTEUR_A, A)).doesNotContain("DISSERT");
    // Le code libéré se réutilise.
    assertThat(
            post(DIRECTEUR_A, A, MATIERES, matiere("DISSERT", "Dissertation", SEMAINE_CYCLES))
                .statut())
        .isEqualTo(201);
  }

  private static final List<String> SEMAINE_CYCLES = List.of("SECONDAIRE");

  @Test
  void lePersonnelLitLesMatieresEtSeulsDirectionCenseurSecretariatLesModifient() throws Exception {
    for (UUID lecteur : List.of(SURVEILLANT_A, ENSEIGNANT_A_PARENT_B, COMPTABLE_A)) {
      assertThat(get(lecteur, A, MATIERES).statut()).isEqualTo(200);
      assertThat(post(lecteur, A, MATIERES, matiere("INTERDIT", "Non", SEMAINE_CYCLES)).statut())
          .isEqualTo(403);
      assertThat(post(lecteur, A, MATIERES + "/modele", null).statut()).isEqualTo(403);
    }
    for (UUID exterieur : List.of(PARENT_A, ELEVE_A)) {
      assertThat(get(exterieur, A, MATIERES).statut()).isEqualTo(403);
    }
    String maths = matiereParCode("MATHS").path("id").asText();
    assertThat(delete(SURVEILLANT_A, A, MATIERES + "/" + maths).statut()).isEqualTo(403);
  }

  @Test
  void lEcoleBNeVoitNiNeModifieLesMatieresDeLEcoleA() throws Exception {
    JsonNode maths = matiereParCode("MATHS");
    String url = MATIERES + "/" + maths.path("id").asText();
    assertThat(
            put(
                    DIRECTEUR_B,
                    B,
                    url,
                    maths.path("version").asInt(),
                    matiere("MATHS", "Pirate", SEMAINE_CYCLES))
                .statut())
        .isEqualTo(404);
    assertThat(delete(DIRECTEUR_B, B, url).statut()).isEqualTo(404);
    assertThat(
            post(DIRECTEUR_B, B, MATIERES, matiere("PROPRE", "Matière de B", SEMAINE_CYCLES))
                .statut())
        .isEqualTo(201);
    assertThat(codes(DIRECTEUR_A, A)).doesNotContain("PROPRE");
    assertThat(matiereParCode("MATHS").path("libelle").asText()).isEqualTo("Mathématiques");
  }

  // --- Grille horaire ---

  @Test
  void laGrilleRefuseLesChevauchementsUnMemeJour() throws Exception {
    UUID moyen = cycle(DIRECTEUR_A, A, "MOYEN");
    String url = "/api/v1/cycles/" + moyen + "/creneaux";
    Reponse matin = post(CENSEUR_A, A, url, creneau("08:00", "10:00", SEMAINE));
    assertThat(matin.statut()).as(matin.corps().toString()).isEqualTo(201);
    assertThat(matin.corps().path("type").asText()).isEqualTo("COURS");
    assertThat(matin.corps().path("jours")).hasSize(5);

    Reponse chevauche = post(DIRECTEUR_A, A, url, creneau("09:00", "11:00", List.of("MONDAY")));
    assertThat(chevauche.statut()).isEqualTo(409);
    assertThat(chevauche.code()).isEqualTo("CRENEAU_CHEVAUCHEMENT");
    // Se toucher n'est pas chevaucher ; un samedi ne gêne pas la semaine.
    assertThat(
            post(
                    DIRECTEUR_A,
                    A,
                    url,
                    corps(
                        "debut",
                        "10:00",
                        "fin",
                        "10:15",
                        "jours",
                        SEMAINE,
                        "type",
                        "PAUSE",
                        "libelle",
                        "Récréation"))
                .statut())
        .isEqualTo(201);
    assertThat(post(DIRECTEUR_A, A, url, creneau("09:00", "11:00", List.of("SATURDAY"))).statut())
        .isEqualTo(201);

    JsonNode grille = get(SURVEILLANT_A, A, url).corps();
    assertThat(grille).hasSize(3);
    assertThat(grille.get(0).path("debut").asText()).startsWith("08:00");

    // Modifier en chevauchant est refusé ; sans chevauchement, accepté.
    String id = matin.id().toString();
    assertThat(
            put(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/creneaux/" + id,
                    version(matin),
                    creneau("08:00", "10:30", SEMAINE))
                .code())
        .isEqualTo("CRENEAU_CHEVAUCHEMENT");
    Reponse avance =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/creneaux/" + id,
            version(matin),
            creneau("07:30", "09:30", SEMAINE));
    assertThat(avance.statut()).isEqualTo(200);
    assertThat(delete(DIRECTEUR_A, A, "/api/v1/creneaux/" + id).statut()).isEqualTo(204);
    assertThat(get(DIRECTEUR_A, A, url).corps()).hasSize(2);
  }

  @Test
  void lesHorairesEtLesJoursSontControles() throws Exception {
    String url = "/api/v1/cycles/" + cycle(DIRECTEUR_A, A, "ELEMENTAIRE") + "/creneaux";
    for (Object invalide :
        List.of(
            creneau("05:30", "07:00", SEMAINE),
            creneau("19:00", "20:30", SEMAINE),
            creneau("10:00", "09:00", SEMAINE),
            creneau("10:00", "10:10", SEMAINE))) {
      Reponse r = post(DIRECTEUR_A, A, url, invalide);
      assertThat(r.statut()).as(invalide.toString()).isEqualTo(400);
      assertThat(r.code()).isEqualTo("HORAIRE_INVALIDE");
    }
    assertThat(post(DIRECTEUR_A, A, url, creneau("08:00", "12:00", List.of())).statut())
        .isEqualTo(400);
  }

  @Test
  void uneGrilleSeCopieDansUnCycleVide() throws Exception {
    UUID elementaire = cycle(DIRECTEUR_A, A, "ELEMENTAIRE");
    UUID moyen = cycle(DIRECTEUR_A, A, "MOYEN");
    post(
        DIRECTEUR_A,
        A,
        "/api/v1/cycles/" + moyen + "/creneaux",
        creneau("08:00", "10:00", SEMAINE));
    post(
        DIRECTEUR_A,
        A,
        "/api/v1/cycles/" + moyen + "/creneaux",
        creneau("10:15", "12:15", SEMAINE));

    String copie = "/api/v1/cycles/" + elementaire + "/creneaux/copie";
    Reponse r = post(SECRETARIAT_A, A, copie, corps("depuisCycleId", moyen));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    assertThat(r.corps()).hasSize(2);
    assertThat(r.corps().get(0).path("cycleId").asText()).isEqualTo(elementaire.toString());

    Reponse encore = post(DIRECTEUR_A, A, copie, corps("depuisCycleId", moyen));
    assertThat(encore.statut()).isEqualTo(409);
    assertThat(encore.code()).isEqualTo("GRILLE_NON_VIDE");
    assertThat(
            post(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/cycles/" + moyen + "/creneaux/copie",
                    corps("depuisCycleId", moyen))
                .code())
        .isEqualTo("COPIE_INVALIDE");
  }

  @Test
  void laGrilleResteDansLEcoleEtSaModificationAuxRolesPedagogiques() throws Exception {
    UUID moyenA = cycle(DIRECTEUR_A, A, "MOYEN");
    String urlA = "/api/v1/cycles/" + moyenA + "/creneaux";
    Reponse cree = post(DIRECTEUR_A, A, urlA, creneau("08:00", "10:00", SEMAINE));

    // L'école B ne lit ni n'écrit la grille de A, ni ne la copie chez elle.
    assertThat(get(DIRECTEUR_B, B, urlA).statut()).isEqualTo(404);
    assertThat(post(DIRECTEUR_B, B, urlA, creneau("14:00", "16:00", SEMAINE)).statut())
        .isEqualTo(404);
    assertThat(
            put(
                    DIRECTEUR_B,
                    B,
                    "/api/v1/creneaux/" + cree.id(),
                    version(cree),
                    creneau("07:00", "08:00", SEMAINE))
                .statut())
        .isEqualTo(404);
    assertThat(delete(DIRECTEUR_B, B, "/api/v1/creneaux/" + cree.id()).statut()).isEqualTo(404);
    UUID moyenB = cycle(DIRECTEUR_B, B, "MOYEN");
    assertThat(
            post(
                    DIRECTEUR_B,
                    B,
                    "/api/v1/cycles/" + moyenB + "/creneaux/copie",
                    corps("depuisCycleId", moyenA))
                .statut())
        .isEqualTo(404);
    assertThat(get(DIRECTEUR_A, A, urlA).corps()).hasSize(1);

    // Lecture par le personnel, écriture par direction, censeur et secrétariat seulement.
    assertThat(get(ENSEIGNANT_A_PARENT_B, A, urlA).statut()).isEqualTo(200);
    assertThat(get(PARENT_A, A, urlA).statut()).isEqualTo(403);
    for (UUID lecteur : List.of(SURVEILLANT_A, ENSEIGNANT_A_PARENT_B, COMPTABLE_A)) {
      assertThat(post(lecteur, A, urlA, creneau("14:00", "16:00", SEMAINE)).statut())
          .isEqualTo(403);
    }
    assertThat(delete(SURVEILLANT_A, A, "/api/v1/creneaux/" + cree.id()).statut()).isEqualTo(403);
  }
}
