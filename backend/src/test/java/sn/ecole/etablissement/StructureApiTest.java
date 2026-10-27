package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.ELEVE_A;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.PARENT_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Cycles, niveaux et séries : modèle sénégalais copié dans chaque école, droits, règles. */
class StructureApiTest extends TestEtablissement {

  private JsonNode niveauJson(UUID utilisateur, UUID ecole, String code) throws Exception {
    for (JsonNode n : get(utilisateur, ecole, "/api/v1/niveaux").corps()) {
      if (code.equals(n.path("code").asText())) {
        return n;
      }
    }
    throw new AssertionError("Niveau introuvable : " + code);
  }

  private static Object corpsCycle(String libelle, String type, String mode, int base) {
    return corps(
        "libelle", libelle, "typePeriode", type, "modeNotation", mode, "baseMoyenne", base);
  }

  // --- Modèle sénégalais ---

  @Test
  void chaqueEcoleRecoitLeModeleSenegalais() throws Exception {
    JsonNode cycles = get(DIRECTEUR_A, A, "/api/v1/cycles").corps();
    assertThat(cycles).hasSize(3);
    assertThat(cycles.get(0).path("code").asText()).isEqualTo("ELEMENTAIRE");
    assertThat(cycles.get(0).path("typePeriode").asText()).isEqualTo("TRIMESTRE");
    assertThat(cycles.get(0).path("modeNotation").asText()).isEqualTo("BAREME");
    assertThat(cycles.get(0).path("baseMoyenne").asInt()).isEqualTo(10);
    assertThat(cycles.get(1).path("code").asText()).isEqualTo("MOYEN");
    assertThat(cycles.get(2).path("typePeriode").asText()).isEqualTo("SEMESTRE");
    assertThat(cycles.get(2).path("modeNotation").asText()).isEqualTo("COEFFICIENT");
    assertThat(cycles.get(2).path("baseMoyenne").asInt()).isEqualTo(20);

    JsonNode niveaux = get(DIRECTEUR_A, A, "/api/v1/niveaux").corps();
    assertThat(niveaux).hasSize(13);
    assertThat(niveaux.get(0).path("code").asText()).isEqualTo("CI");
    assertThat(niveaux.get(12).path("code").asText()).isEqualTo("TLE");
    assertThat(niveauJson(DIRECTEUR_A, A, "CP").path("serieIds")).isEmpty();
    assertThat(niveauJson(DIRECTEUR_A, A, "2NDE").path("serieIds")).hasSize(2);
    assertThat(niveauJson(DIRECTEUR_A, A, "1RE").path("serieIds")).hasSize(5);
    assertThat(niveauJson(DIRECTEUR_A, A, "TLE").path("serieIds")).hasSize(5);
    assertThat(get(DIRECTEUR_A, A, "/api/v1/series").corps()).hasSize(7);
  }

  @Test
  void laBaseEffectiveSuitLeCycleSaufSurchargeDuNiveau() throws Exception {
    assertThat(niveauJson(DIRECTEUR_A, A, "CE1").path("baseMoyenneEffective").asInt())
        .isEqualTo(10);
    assertThat(niveauJson(DIRECTEUR_A, A, "CE1").path("baseMoyenne").isNull()).isTrue();
    assertThat(niveauJson(DIRECTEUR_A, A, "6E").path("baseMoyenneEffective").asInt()).isEqualTo(20);

    JsonNode ce1 = niveauJson(DIRECTEUR_A, A, "CE1");
    Reponse r =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux/" + ce1.path("id").asText(),
            ce1.path("version").asInt(),
            corps("libelle", "CE1", "ordre", 3, "baseMoyenne", 20, "actif", true));
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("baseMoyenne").asInt()).isEqualTo(20);
    assertThat(r.corps().path("baseMoyenneEffective").asInt()).isEqualTo(20);
    // Le cycle, lui, est inchangé.
    assertThat(get(DIRECTEUR_A, A, "/api/v1/cycles").corps().get(0).path("baseMoyenne").asInt())
        .isEqualTo(10);

    Reponse retour =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux/" + ce1.path("id").asText(),
            r.version(),
            corps("libelle", "CE1", "ordre", 3, "baseMoyenne", null, "actif", true));
    assertThat(retour.corps().path("baseMoyenneEffective").asInt()).isEqualTo(10);
  }

  // --- Cycles ---

  @Test
  void lePersonnelLitLesCyclesMaisPasLesParentsNiLesEleves() throws Exception {
    assertThat(get(CENSEUR_A, A, "/api/v1/cycles").statut()).isEqualTo(200);
    assertThat(get(ENSEIGNANT_A_PARENT_B, A, "/api/v1/cycles").statut()).isEqualTo(200);
    assertThat(get(PARENT_A, A, "/api/v1/cycles").statut()).isEqualTo(403);
    assertThat(get(ELEVE_A, A, "/api/v1/cycles").statut()).isEqualTo(403);
    assertThat(get(PARENT_A, A, "/api/v1/niveaux").statut()).isEqualTo(403);
    assertThat(get(ELEVE_A, A, "/api/v1/series").statut()).isEqualTo(403);
  }

  @Test
  void seulLeDirecteurModifieUnCycle() throws Exception {
    JsonNode cycle = get(DIRECTEUR_A, A, "/api/v1/cycles").corps().get(1);
    String url = "/api/v1/cycles/" + cycle.path("id").asText();
    int version = cycle.path("version").asInt();
    Object nouveau = corpsCycle("Moyen (trimestres)", "TRIMESTRE", "COEFFICIENT", 20);

    for (UUID autre : new UUID[] {SECRETARIAT_A, CENSEUR_A, ENSEIGNANT_A_PARENT_B, PARENT_A}) {
      assertThat(put(autre, A, url, version, nouveau).statut()).as("par %s", autre).isEqualTo(403);
    }
    Reponse r = put(DIRECTEUR_A, A, url, version, nouveau);
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("typePeriode").asText()).isEqualTo("TRIMESTRE");
  }

  @Test
  void laBaseDeLaMoyenneDUnCycleVaut10Ou20() throws Exception {
    JsonNode cycle = get(DIRECTEUR_A, A, "/api/v1/cycles").corps().get(1);
    Reponse r =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/cycles/" + cycle.path("id").asText(),
            cycle.path("version").asInt(),
            corpsCycle("Moyen", "SEMESTRE", "COEFFICIENT", 15));
    assertThat(r.statut()).isEqualTo(400);
    assertThat(r.code()).isEqualTo("BASE_MOYENNE_INVALIDE");
  }

  @Test
  void lesParametresDUnCycleSontVerrouillesUneFoisUneAnneeOuverte() throws Exception {
    JsonNode cycle = get(DIRECTEUR_A, A, "/api/v1/cycles").corps().get(2);
    String url = "/api/v1/cycles/" + cycle.path("id").asText();
    creerEtOuvrirAnnee(DIRECTEUR_A, A);
    int version = get(DIRECTEUR_A, A, "/api/v1/cycles").corps().get(2).path("version").asInt();

    Reponse change =
        put(DIRECTEUR_A, A, url, version, corpsCycle("Secondaire", "TRIMESTRE", "COEFFICIENT", 20));
    assertThat(change.statut()).isEqualTo(409);
    assertThat(change.code()).isEqualTo("CYCLE_VERROUILLE");

    // Renommer, sans toucher aux paramètres, reste possible.
    Reponse libelle =
        put(
            DIRECTEUR_A,
            A,
            url,
            version,
            corpsCycle("Second cycle", "SEMESTRE", "COEFFICIENT", 20));
    assertThat(libelle.statut()).isEqualTo(200);
  }

  @Test
  void uneVersionPerimeeDUnCycleDonne409() throws Exception {
    JsonNode cycle = get(DIRECTEUR_A, A, "/api/v1/cycles").corps().get(0);
    String url = "/api/v1/cycles/" + cycle.path("id").asText();
    int version = cycle.path("version").asInt();
    put(DIRECTEUR_A, A, url, version, corpsCycle("Élémentaire", "TRIMESTRE", "BAREME", 20));
    Reponse r =
        put(DIRECTEUR_A, A, url, version, corpsCycle("Élémentaire", "TRIMESTRE", "BAREME", 10));
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("CONFLIT_VERSION");
  }

  // --- Niveaux ---

  @Test
  void leSecretariatCreeUnNiveauMaisSansBaseDeMoyenne() throws Exception {
    UUID cycle = cycle(SECRETARIAT_A, A, "SECONDAIRE");
    Reponse sans =
        post(
            SECRETARIAT_A,
            A,
            "/api/v1/niveaux",
            corps("cycleId", cycle, "code", "pc", "libelle", "Prépa", "ordre", 14));
    assertThat(sans.statut()).isEqualTo(201);
    assertThat(sans.corps().path("code").asText()).isEqualTo("PC");
    assertThat(sans.corps().path("baseMoyenneEffective").asInt()).isEqualTo(20);

    Reponse avecBase =
        post(
            SECRETARIAT_A,
            A,
            "/api/v1/niveaux",
            corps(
                "cycleId",
                cycle,
                "code",
                "PC2",
                "libelle",
                "Prépa 2",
                "ordre",
                15,
                "baseMoyenne",
                10));
    assertThat(avecBase.statut()).isEqualTo(403);

    Reponse directeur =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux",
            corps(
                "cycleId",
                cycle,
                "code",
                "PC2",
                "libelle",
                "Prépa 2",
                "ordre",
                15,
                "baseMoyenne",
                10));
    assertThat(directeur.statut()).isEqualTo(201);
    assertThat(directeur.corps().path("baseMoyenneEffective").asInt()).isEqualTo(10);
  }

  @Test
  void seulLeDirecteurChangeLaBaseDUnNiveau() throws Exception {
    JsonNode cp = niveauJson(DIRECTEUR_A, A, "CP");
    String url = "/api/v1/niveaux/" + cp.path("id").asText();
    int version = cp.path("version").asInt();

    Reponse refuse =
        put(
            SECRETARIAT_A,
            A,
            url,
            version,
            corps("libelle", "CP", "ordre", 2, "baseMoyenne", 20, "actif", true));
    assertThat(refuse.statut()).isEqualTo(403);

    // Le secrétariat peut renommer ou réordonner sans toucher à la base.
    Reponse renomme =
        put(
            SECRETARIAT_A,
            A,
            url,
            version,
            corps("libelle", "Cours préparatoire", "ordre", 2, "actif", true));
    assertThat(renomme.statut()).isEqualTo(200);
    assertThat(renomme.corps().path("libelle").asText()).isEqualTo("Cours préparatoire");
  }

  @Test
  void laBaseDUnNiveauEstVerrouilleeUneFoisUneAnneeOuverte() throws Exception {
    creerEtOuvrirAnnee(DIRECTEUR_A, A);
    JsonNode cp = niveauJson(DIRECTEUR_A, A, "CP");
    Reponse r =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux/" + cp.path("id").asText(),
            cp.path("version").asInt(),
            corps("libelle", "CP", "ordre", 2, "baseMoyenne", 20, "actif", true));
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("CYCLE_VERROUILLE");
  }

  @Test
  void deuxNiveauxNePartagentPasLeMemeCode() throws Exception {
    UUID cycle = cycle(DIRECTEUR_A, A, "MOYEN");
    Reponse r =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux",
            corps("cycleId", cycle, "code", "6e", "libelle", "Autre", "ordre", 20));
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("NIVEAU_EXISTANT");
  }

  @Test
  void lesDonneesInvalidesDUnNiveauDonnent400() throws Exception {
    UUID cycle = cycle(DIRECTEUR_A, A, "MOYEN");
    assertThat(
            post(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/niveaux",
                    corps("cycleId", cycle, "code", "", "libelle", "X", "ordre", 1))
                .code())
        .isEqualTo("VALIDATION");
    assertThat(
            post(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/niveaux",
                    corps("cycleId", cycle, "code", "A B", "libelle", "X", "ordre", 1))
                .code())
        .isEqualTo("CHAMP_INVALIDE");
    assertThat(
            post(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/niveaux",
                    corps("cycleId", cycle, "code", "AB", "libelle", "X", "ordre", 0))
                .code())
        .isEqualTo("CHAMP_INVALIDE");
    Reponse cycleInconnu =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux",
            corps("cycleId", UUID.randomUUID(), "code", "AB", "libelle", "X", "ordre", 1));
    assertThat(cycleInconnu.statut()).isEqualTo(404);
    assertThat(cycleInconnu.code()).isEqualTo("CYCLE_INTROUVABLE");
  }

  @Test
  void leSecretariatDefinitLesSeriesDUnNiveau() throws Exception {
    UUID cp = niveau(DIRECTEUR_A, A, "CP");
    UUID serieL = serie(DIRECTEUR_A, A, "L");
    String url = "/api/v1/niveaux/" + cp + "/series";

    Reponse r = putSansVersion(SECRETARIAT_A, A, url, corps("serieIds", List.of(serieL)));
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("serieIds")).hasSize(1);

    Reponse vide = putSansVersion(SECRETARIAT_A, A, url, corps("serieIds", List.of()));
    assertThat(vide.corps().path("serieIds")).isEmpty();

    Reponse inconnue =
        putSansVersion(SECRETARIAT_A, A, url, corps("serieIds", List.of(UUID.randomUUID())));
    assertThat(inconnue.statut()).isEqualTo(404);
    assertThat(inconnue.code()).isEqualTo("SERIE_INTROUVABLE");

    assertThat(putSansVersion(CENSEUR_A, A, url, corps("serieIds", List.of())).statut())
        .isEqualTo(403);
  }

  @Test
  void onNePeutPasRetirerUneSerieUtiliseeParUneClasse() throws Exception {
    creerEtOuvrirAnnee(DIRECTEUR_A, A);
    UUID tle = niveau(DIRECTEUR_A, A, "TLE");
    UUID s2 = serie(DIRECTEUR_A, A, "S2");
    UUID annee = get(DIRECTEUR_A, A, "/api/v1/annees-scolaires/en-cours").id();
    assertThat(
            post(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/classes",
                    corps(
                        "anneeId",
                        annee,
                        "niveauId",
                        tle,
                        "serieId",
                        s2,
                        "nom",
                        "TS2B",
                        "capacite",
                        45))
                .statut())
        .isEqualTo(201);

    List<UUID> sansS2 = new ArrayList<>();
    for (JsonNode id : niveauJson(DIRECTEUR_A, A, "TLE").path("serieIds")) {
      if (!id.asText().equals(s2.toString())) {
        sansS2.add(UUID.fromString(id.asText()));
      }
    }
    Reponse r =
        putSansVersion(
            DIRECTEUR_A, A, "/api/v1/niveaux/" + tle + "/series", corps("serieIds", sansS2));
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("SERIE_UTILISEE");

    // Un niveau avec séries ne peut pas devenir « sans série » tant que des classes l'utilisent.
    Reponse toutes =
        putSansVersion(
            DIRECTEUR_A, A, "/api/v1/niveaux/" + tle + "/series", corps("serieIds", List.of()));
    assertThat(toutes.statut()).isEqualTo(409);
    assertThat(toutes.code()).isEqualTo("NIVEAU_UTILISE");
  }

  @Test
  void onNeSupprimePasUnNiveauAvecDesClasses() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    UUID ce1 = niveau(DIRECTEUR_A, A, "CE1");
    UUID cm2 = niveau(DIRECTEUR_A, A, "CM2");
    post(
        DIRECTEUR_A,
        A,
        "/api/v1/classes",
        corps("anneeId", annee, "niveauId", ce1, "nom", "CE1A", "capacite", 40));

    Reponse r = delete(DIRECTEUR_A, A, "/api/v1/niveaux/" + ce1);
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("NIVEAU_UTILISE");

    assertThat(delete(SECRETARIAT_A, A, "/api/v1/niveaux/" + cm2).statut()).isEqualTo(204);
    assertThat(get(DIRECTEUR_A, A, "/api/v1/niveaux/" + cm2).statut()).isEqualTo(404);
  }

  // --- Séries ---

  @Test
  void cycleDeVieDUneSerie() throws Exception {
    Reponse creee =
        post(SECRETARIAT_A, A, "/api/v1/series", corps("code", "s4", "libelle", "Série S4"));
    assertThat(creee.statut()).isEqualTo(201);
    assertThat(creee.corps().path("code").asText()).isEqualTo("S4");

    Reponse doublon =
        post(DIRECTEUR_A, A, "/api/v1/series", corps("code", "S4", "libelle", "Autre"));
    assertThat(doublon.statut()).isEqualTo(409);
    assertThat(doublon.code()).isEqualTo("SERIE_EXISTANTE");

    String url = "/api/v1/series/" + creee.id();
    Reponse modifiee =
        put(
            DIRECTEUR_A,
            A,
            url,
            creee.version(),
            corps("code", "S4", "libelle", "Série S4 bis", "actif", false));
    assertThat(modifiee.statut()).isEqualTo(200);
    assertThat(modifiee.corps().path("actif").asBoolean()).isFalse();

    assertThat(
            put(CENSEUR_A, A, url, modifiee.version(), corps("code", "S4", "libelle", "x"))
                .statut())
        .isEqualTo(403);
    assertThat(delete(SECRETARIAT_A, A, url).statut()).isEqualTo(204);
    assertThat(
            post(DIRECTEUR_A, A, "/api/v1/series", corps("code", "S4", "libelle", "Recréée"))
                .statut())
        .isEqualTo(201);
  }

  @Test
  void onNeSupprimePasUneSerieProposeeParUnNiveau() throws Exception {
    Reponse r = delete(DIRECTEUR_A, A, "/api/v1/series/" + serie(DIRECTEUR_A, A, "S1"));
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("SERIE_UTILISEE");
  }

  @Test
  void leSecretariatNeContournePasLaBaseDuDirecteurEnSupprimantLeNiveau() throws Exception {
    JsonNode cp = niveauJson(DIRECTEUR_A, A, "CP");
    String url = "/api/v1/niveaux/" + cp.path("id").asText();
    put(
        DIRECTEUR_A,
        A,
        url,
        cp.path("version").asInt(),
        corps("libelle", "CP", "ordre", 2, "baseMoyenne", 20, "actif", true));

    assertThat(delete(SECRETARIAT_A, A, url).statut()).isEqualTo(403);
    assertThat(get(DIRECTEUR_A, A, url).statut()).isEqualTo(200);
    assertThat(delete(DIRECTEUR_A, A, url).statut()).isEqualTo(204);
  }

  @Test
  void laListeDeSeriesEstBornee() throws Exception {
    UUID cp = niveau(DIRECTEUR_A, A, "CP");
    List<UUID> trop = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      trop.add(UUID.randomUUID());
    }
    Reponse r =
        putSansVersion(
            DIRECTEUR_A, A, "/api/v1/niveaux/" + cp + "/series", corps("serieIds", trop));
    assertThat(r.statut()).isEqualTo(400);
    assertThat(r.code()).isEqualTo("VALIDATION");
  }

  // --- Isolement ---

  @Test
  void lEcoleBNeVoitNiNeModifieLaStructureDeLEcoleA() throws Exception {
    UUID niveauA = niveau(DIRECTEUR_A, A, "TLE");
    UUID serieA = serie(DIRECTEUR_A, A, "S1");
    UUID cycleA = cycle(DIRECTEUR_A, A, "SECONDAIRE");
    UUID niveauB = niveau(DIRECTEUR_B, B, "TLE");
    assertThat(niveauB).isNotEqualTo(niveauA);

    assertThat(get(DIRECTEUR_B, B, "/api/v1/niveaux/" + niveauA).statut()).isEqualTo(404);
    assertThat(
            put(
                    DIRECTEUR_B,
                    B,
                    "/api/v1/niveaux/" + niveauA,
                    0,
                    corps("libelle", "X", "ordre", 1, "actif", true))
                .statut())
        .isEqualTo(404);
    assertThat(delete(DIRECTEUR_B, B, "/api/v1/niveaux/" + niveauA).statut()).isEqualTo(404);
    assertThat(
            putSansVersion(
                    DIRECTEUR_B,
                    B,
                    "/api/v1/niveaux/" + niveauA + "/series",
                    corps("serieIds", List.of()))
                .statut())
        .isEqualTo(404);
    assertThat(
            put(
                    DIRECTEUR_B,
                    B,
                    "/api/v1/cycles/" + cycleA,
                    0,
                    corpsCycle("X", "SEMESTRE", "COEFFICIENT", 20))
                .statut())
        .isEqualTo(404);
    assertThat(delete(DIRECTEUR_B, B, "/api/v1/series/" + serieA).statut()).isEqualTo(404);
    assertThat(
            put(DIRECTEUR_B, B, "/api/v1/series/" + serieA, 0, corps("code", "X", "libelle", "X"))
                .statut())
        .isEqualTo(404);

    // Une série de A ne peut pas être associée à un niveau de B.
    Reponse croise =
        putSansVersion(
            DIRECTEUR_B,
            B,
            "/api/v1/niveaux/" + niveauB + "/series",
            corps("serieIds", List.of(serieA)));
    assertThat(croise.statut()).isEqualTo(404);
    assertThat(croise.code()).isEqualTo("SERIE_INTROUVABLE");

    // Rien n'a changé chez A.
    assertThat(get(DIRECTEUR_A, A, "/api/v1/niveaux").corps()).hasSize(13);
    assertThat(niveauJson(DIRECTEUR_A, A, "TLE").path("serieIds")).hasSize(5);
    // Et le directeur de B ne peut pas désigner A.
    assertThat(get(DIRECTEUR_B, A, "/api/v1/niveaux").statut()).isEqualTo(403);
  }
}
