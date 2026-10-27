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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Classes (nom unique par année, série cohérente avec le niveau) et salles. */
class ClassesEtSallesApiTest extends TestEtablissement {

  private static final String CLASSES = "/api/v1/classes";
  private static final String SALLES = "/api/v1/salles";

  private UUID annee;

  @BeforeEach
  void anneeEnCours() throws Exception {
    annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
  }

  private Object classe(UUID niveau, UUID serie, String nom, int capacite, UUID salle) {
    return corps(
        "anneeId",
        annee,
        "niveauId",
        niveau,
        "serieId",
        serie,
        "nom",
        nom,
        "capacite",
        capacite,
        "salleId",
        salle);
  }

  private Reponse creerClasse(String niveauCode, String serieCode, String nom) throws Exception {
    UUID serie = serieCode == null ? null : serie(DIRECTEUR_A, A, serieCode);
    return post(
        SECRETARIAT_A,
        A,
        CLASSES,
        classe(niveau(DIRECTEUR_A, A, niveauCode), serie, nom, 45, null));
  }

  private List<String> noms(Reponse r) {
    List<String> noms = new ArrayList<>();
    r.corps().forEach(c -> noms.add(c.path("nom").asText()));
    return noms;
  }

  // --- Création et cohérence série / niveau ---

  @Test
  void leSecretariatCreeUneClasseAvecSerieEtSalle() throws Exception {
    Reponse salle = post(SECRETARIAT_A, A, SALLES, corps("nom", "Salle 12", "capacite", 50));
    Reponse r =
        post(
            SECRETARIAT_A,
            A,
            CLASSES,
            classe(
                niveau(DIRECTEUR_A, A, "TLE"),
                serie(DIRECTEUR_A, A, "S2"),
                "TS2B",
                45,
                salle.id()));
    assertThat(r.statut()).isEqualTo(201);
    assertThat(r.corps().path("nom").asText()).isEqualTo("TS2B");
    assertThat(r.corps().path("anneeId").asText()).isEqualTo(annee.toString());
    assertThat(r.corps().path("salleId").asText()).isEqualTo(salle.id().toString());
    assertThat(r.brut().getHeader("Location")).endsWith(CLASSES + "/" + r.id());
  }

  @Test
  void uneSerieEstObligatoireSeulementSiLeNiveauEnA() throws Exception {
    Reponse requise = creerClasse("TLE", null, "TLE-X");
    assertThat(requise.statut()).isEqualTo(422);
    assertThat(requise.code()).isEqualTo("SERIE_REQUISE");

    Reponse interdite = creerClasse("CP", "S2", "CPA");
    assertThat(interdite.statut()).isEqualTo(422);
    assertThat(interdite.code()).isEqualTo("SERIE_INTERDITE");

    // S2 n'est pas proposée en 2nde (L et S seulement).
    assertThat(creerClasse("2NDE", "S2", "2S2").code()).isEqualTo("SERIE_INTERDITE");
    assertThat(creerClasse("2NDE", "S", "2S1").statut()).isEqualTo(201);
    assertThat(creerClasse("CP", null, "CPA").statut()).isEqualTo(201);
  }

  @Test
  void deuxClassesNePartagentPasLeMemeNomDansUneAnnee() throws Exception {
    assertThat(creerClasse("TLE", "S2", "TS2B").statut()).isEqualTo(201);
    Reponse doublon = creerClasse("TLE", "S1", "ts2b");
    assertThat(doublon.statut()).isEqualTo(409);
    assertThat(doublon.code()).isEqualTo("CLASSE_EXISTANTE");

    // Autre année : le même nom est possible.
    Reponse suivante =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/annees-scolaires",
            corps("libelle", "2027-2028", "dateDebut", "2027-10-04", "dateFin", "2028-07-07"));
    Reponse memeNom =
        post(
            SECRETARIAT_A,
            A,
            CLASSES,
            corps(
                "anneeId",
                suivante.id(),
                "niveauId",
                niveau(DIRECTEUR_A, A, "TLE"),
                "serieId",
                serie(DIRECTEUR_A, A, "S2"),
                "nom",
                "TS2B",
                "capacite",
                40));
    assertThat(memeNom.statut()).isEqualTo(201);
  }

  @Test
  void leNomEstLibereParLaSuppression() throws Exception {
    Reponse c = creerClasse("CP", null, "CPA");
    assertThat(delete(SECRETARIAT_A, A, CLASSES + "/" + c.id()).statut()).isEqualTo(204);
    assertThat(get(DIRECTEUR_A, A, CLASSES + "/" + c.id()).statut()).isEqualTo(404);
    assertThat(creerClasse("CP", null, "CPA").statut()).isEqualTo(201);
    assertThat(delete(SECRETARIAT_A, A, CLASSES + "/" + UUID.randomUUID()).code())
        .isEqualTo("CLASSE_INTROUVABLE");
  }

  @Test
  void lesDonneesInvalidesDUneClasseSontRefusees() throws Exception {
    UUID cp = niveau(DIRECTEUR_A, A, "CP");
    assertThat(post(SECRETARIAT_A, A, CLASSES, classe(cp, null, "CPA", 0, null)).code())
        .isEqualTo("CAPACITE_INVALIDE");
    assertThat(post(SECRETARIAT_A, A, CLASSES, classe(cp, null, "CPA", 501, null)).code())
        .isEqualTo("CAPACITE_INVALIDE");
    assertThat(post(SECRETARIAT_A, A, CLASSES, classe(cp, null, "", 30, null)).code())
        .isEqualTo("VALIDATION");
    assertThat(
            post(SECRETARIAT_A, A, CLASSES, corps("niveauId", cp, "nom", "CPA", "capacite", 30))
                .code())
        .isEqualTo("CHAMP_INVALIDE"); // pas d'année

    Reponse niveauInconnu =
        post(SECRETARIAT_A, A, CLASSES, classe(UUID.randomUUID(), null, "CPA", 30, null));
    assertThat(niveauInconnu.statut()).isEqualTo(404);
    assertThat(niveauInconnu.code()).isEqualTo("NIVEAU_INTROUVABLE");
    assertThat(
            post(SECRETARIAT_A, A, CLASSES, classe(cp, null, "CPA", 30, UUID.randomUUID())).code())
        .isEqualTo("SALLE_INTROUVABLE");
    assertThat(
            post(
                    SECRETARIAT_A,
                    A,
                    CLASSES,
                    corps(
                        "anneeId", UUID.randomUUID(), "niveauId", cp, "nom", "CPA", "capacite", 30))
                .code())
        .isEqualTo("ANNEE_INTROUVABLE");
  }

  @Test
  void unNiveauInactifNeRecoitPlusDeClasse() throws Exception {
    JsonNode cp = get(DIRECTEUR_A, A, "/api/v1/niveaux").corps().get(1);
    put(
        DIRECTEUR_A,
        A,
        "/api/v1/niveaux/" + cp.path("id").asText(),
        cp.path("version").asInt(),
        corps("libelle", "CP", "ordre", 2, "actif", false));
    Reponse r = creerClasse("CP", null, "CPA");
    assertThat(r.statut()).isEqualTo(422);
    assertThat(r.code()).isEqualTo("NIVEAU_INACTIF");
  }

  // --- Modification ---

  @Test
  void onModifieUneClasseAvecSaVersion() throws Exception {
    Reponse c = creerClasse("TLE", "S2", "TS2B");
    String url = CLASSES + "/" + c.id();
    Object nouveau =
        classe(niveau(DIRECTEUR_A, A, "TLE"), serie(DIRECTEUR_A, A, "S2"), "TS1A", 38, null);

    Reponse r = put(SECRETARIAT_A, A, url, c.version(), nouveau);
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("nom").asText()).isEqualTo("TS1A");
    assertThat(r.corps().path("capacite").asInt()).isEqualTo(38);

    Reponse perimee = put(SECRETARIAT_A, A, url, c.version(), nouveau);
    assertThat(perimee.statut()).isEqualTo(409);
    assertThat(perimee.code()).isEqualTo("CONFLIT_VERSION");

    // Garder son propre nom n'est pas un doublon ; prendre celui d'une autre classe l'est.
    assertThat(put(SECRETARIAT_A, A, url, r.version(), nouveau).statut()).isEqualTo(200);
    creerClasse("TLE", "S2", "TS2B");
    Reponse prise =
        put(
            SECRETARIAT_A,
            A,
            url,
            get(DIRECTEUR_A, A, url).version(),
            classe(niveau(DIRECTEUR_A, A, "TLE"), serie(DIRECTEUR_A, A, "S2"), "TS2B", 38, null));
    assertThat(prise.statut()).isEqualTo(409);
    assertThat(prise.code()).isEqualTo("CLASSE_EXISTANTE");

    // Année en cours : le niveau est figé (il fixe la base et les coefficients).
    Reponse figee =
        put(
            SECRETARIAT_A,
            A,
            url,
            get(DIRECTEUR_A, A, url).version(),
            classe(niveau(DIRECTEUR_A, A, "CP"), null, "TS1A", 38, null));
    assertThat(figee.statut()).isEqualTo(409);
    assertThat(figee.code()).isEqualTo("NIVEAU_CLASSE_FIGE");
    // La série est figée comme le niveau.
    Reponse serieFigee =
        put(
            SECRETARIAT_A,
            A,
            url,
            get(DIRECTEUR_A, A, url).version(),
            classe(niveau(DIRECTEUR_A, A, "TLE"), serie(DIRECTEUR_A, A, "S1"), "TS1A", 38, null));
    assertThat(serieFigee.statut()).isEqualTo(409);
    assertThat(serieFigee.code()).isEqualTo("SERIE_CLASSE_FIGEE");
  }

  @Test
  void leNiveauDUneClasseChangeTantQueLAnneeEstEnPreparation() throws Exception {
    Reponse suivante =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/annees-scolaires",
            corps("libelle", "2027-2028", "dateDebut", "2027-10-04", "dateFin", "2028-07-07"));
    Reponse c =
        post(
            SECRETARIAT_A,
            A,
            CLASSES,
            corps(
                "anneeId",
                suivante.id(),
                "niveauId",
                niveau(DIRECTEUR_A, A, "TLE"),
                "serieId",
                serie(DIRECTEUR_A, A, "S2"),
                "nom",
                "TS2B",
                "capacite",
                40));
    String url = CLASSES + "/" + c.id();

    // La série doit toujours correspondre au niveau.
    Reponse incoherente =
        put(
            SECRETARIAT_A,
            A,
            url,
            c.version(),
            corps(
                "niveauId",
                niveau(DIRECTEUR_A, A, "CP"),
                "serieId",
                serie(DIRECTEUR_A, A, "S1"),
                "nom",
                "TS2B",
                "capacite",
                40));
    assertThat(incoherente.code()).isEqualTo("SERIE_INTERDITE");

    Reponse ok =
        put(
            SECRETARIAT_A,
            A,
            url,
            c.version(),
            corps("niveauId", niveau(DIRECTEUR_A, A, "CP"), "nom", "CPA", "capacite", 40));
    assertThat(ok.statut()).isEqualTo(200);
    assertThat(ok.corps().path("serieId").isNull()).isTrue();
  }

  @Test
  void laSerieDUneClasseChangeTantQueLAnneeEstEnPreparation() throws Exception {
    Reponse suivante =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/annees-scolaires",
            corps("libelle", "2027-2028", "dateDebut", "2027-10-04", "dateFin", "2028-07-07"));
    UUID tle = niveau(DIRECTEUR_A, A, "TLE");
    Reponse c =
        post(
            SECRETARIAT_A,
            A,
            CLASSES,
            corps(
                "anneeId",
                suivante.id(),
                "niveauId",
                tle,
                "serieId",
                serie(DIRECTEUR_A, A, "S2"),
                "nom",
                "TS2B",
                "capacite",
                40));

    Reponse ok =
        put(
            SECRETARIAT_A,
            A,
            CLASSES + "/" + c.id(),
            c.version(),
            corps(
                "niveauId",
                tle,
                "serieId",
                serie(DIRECTEUR_A, A, "S1"),
                "nom",
                "TS1A",
                "capacite",
                40));
    assertThat(ok.statut()).isEqualTo(200);
    assertThat(ok.corps().path("serieId").asText())
        .isEqualTo(serie(DIRECTEUR_A, A, "S1").toString());
  }

  // --- Lecture ---

  @Test
  void laListeParDefautEstCelleDeLAnneeEnCoursEtSeFiltreParNiveau() throws Exception {
    creerClasse("TLE", "S2", "TS2B");
    creerClasse("CP", null, "CPA");
    creerClasse("CP", null, "CPB");

    assertThat(noms(get(DIRECTEUR_A, A, CLASSES))).containsExactly("CPA", "CPB", "TS2B");
    assertThat(noms(get(DIRECTEUR_A, A, CLASSES + "?annee=" + annee))).hasSize(3);
    assertThat(noms(get(DIRECTEUR_A, A, CLASSES + "?niveau=" + niveau(DIRECTEUR_A, A, "CP"))))
        .containsExactly("CPA", "CPB");
    assertThat(get(DIRECTEUR_A, A, CLASSES + "?annee=" + UUID.randomUUID()).code())
        .isEqualTo("ANNEE_INTROUVABLE");
  }

  @Test
  void sansAnneeEnCoursLaListeParDefautEstVide() throws Exception {
    post(DIRECTEUR_A, A, "/api/v1/annees-scolaires/" + annee + "/cloture", null);
    Reponse r = get(DIRECTEUR_A, A, CLASSES);
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps()).isEmpty();
  }

  @Test
  void lePersonnelLitLesClassesMaisPasLesParentsNiLesEleves() throws Exception {
    Reponse c = creerClasse("CP", null, "CPA");
    for (UUID lecteur :
        new UUID[] {
          DIRECTEUR_A, CENSEUR_A, SECRETARIAT_A, SURVEILLANT_A, ENSEIGNANT_A_PARENT_B, COMPTABLE_A
        }) {
      assertThat(get(lecteur, A, CLASSES).statut()).as("liste par %s", lecteur).isEqualTo(200);
      assertThat(get(lecteur, A, CLASSES + "/" + c.id()).statut()).isEqualTo(200);
    }
    for (UUID interdit : new UUID[] {PARENT_A, ELEVE_A}) {
      assertThat(get(interdit, A, CLASSES).statut()).as("liste par %s", interdit).isEqualTo(403);
      assertThat(get(interdit, A, CLASSES + "/" + c.id()).statut()).isEqualTo(403);
    }
  }

  @Test
  void seulsDirecteurEtSecretariatEcriventLesClasses() throws Exception {
    Reponse c = creerClasse("CP", null, "CPA");
    UUID cp = niveau(DIRECTEUR_A, A, "CP");
    for (UUID autre :
        new UUID[] {
          CENSEUR_A, SURVEILLANT_A, ENSEIGNANT_A_PARENT_B, COMPTABLE_A, PARENT_A, ELEVE_A
        }) {
      assertThat(post(autre, A, CLASSES, classe(cp, null, "CPZ", 30, null)).statut())
          .as("POST par %s", autre)
          .isEqualTo(403);
      assertThat(
              put(autre, A, CLASSES + "/" + c.id(), c.version(), classe(cp, null, "CPZ", 30, null))
                  .statut())
          .as("PUT par %s", autre)
          .isEqualTo(403);
      assertThat(delete(autre, A, CLASSES + "/" + c.id()).statut())
          .as("DELETE par %s", autre)
          .isEqualTo(403);
    }
    assertThat(noms(get(DIRECTEUR_A, A, CLASSES))).containsExactly("CPA");
  }

  // --- Salles ---

  @Test
  void cycleDeVieDUneSalle() throws Exception {
    Reponse s = post(SECRETARIAT_A, A, SALLES, corps("nom", "Salle 1", "capacite", 40));
    assertThat(s.statut()).isEqualTo(201);

    Reponse doublon = post(DIRECTEUR_A, A, SALLES, corps("nom", "salle 1", "capacite", 30));
    assertThat(doublon.statut()).isEqualTo(409);
    assertThat(doublon.code()).isEqualTo("SALLE_EXISTANTE");
    assertThat(post(DIRECTEUR_A, A, SALLES, corps("nom", "Salle 2", "capacite", 0)).code())
        .isEqualTo("CAPACITE_INVALIDE");

    Reponse modifiee =
        put(
            SECRETARIAT_A,
            A,
            SALLES + "/" + s.id(),
            s.version(),
            corps("nom", "Salle 1B", "capacite", 45, "actif", false));
    assertThat(modifiee.statut()).isEqualTo(200);
    assertThat(modifiee.corps().path("actif").asBoolean()).isFalse();
    assertThat(
            put(
                    SECRETARIAT_A,
                    A,
                    SALLES + "/" + s.id(),
                    s.version(),
                    corps("nom", "X", "capacite", 5))
                .code())
        .isEqualTo("CONFLIT_VERSION");

    assertThat(delete(SECRETARIAT_A, A, SALLES + "/" + s.id()).statut()).isEqualTo(204);
    assertThat(get(DIRECTEUR_A, A, SALLES + "/" + s.id()).statut()).isEqualTo(404);
    assertThat(get(DIRECTEUR_A, A, SALLES).corps()).isEmpty();
  }

  @Test
  void uneSalleUtiliseeParUneClasseNeSePasSupprime() throws Exception {
    Reponse s = post(SECRETARIAT_A, A, SALLES, corps("nom", "Salle 1", "capacite", 40));
    post(SECRETARIAT_A, A, CLASSES, classe(niveau(DIRECTEUR_A, A, "CP"), null, "CPA", 30, s.id()));
    Reponse r = delete(DIRECTEUR_A, A, SALLES + "/" + s.id());
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("SALLE_UTILISEE");
  }

  @Test
  void droitsSurLesSalles() throws Exception {
    Reponse s = post(SECRETARIAT_A, A, SALLES, corps("nom", "Salle 1", "capacite", 40));
    for (UUID lecteur : new UUID[] {CENSEUR_A, SURVEILLANT_A, ENSEIGNANT_A_PARENT_B, COMPTABLE_A}) {
      assertThat(get(lecteur, A, SALLES).statut()).isEqualTo(200);
      assertThat(post(lecteur, A, SALLES, corps("nom", "Intrus", "capacite", 10)).statut())
          .isEqualTo(403);
      assertThat(delete(lecteur, A, SALLES + "/" + s.id()).statut()).isEqualTo(403);
    }
    for (UUID interdit : new UUID[] {PARENT_A, ELEVE_A}) {
      assertThat(get(interdit, A, SALLES).statut()).isEqualTo(403);
    }
  }

  // --- Isolement ---

  @Test
  void lEcoleBNeVoitNiNeModifieLesClassesEtSallesDeLEcoleA() throws Exception {
    Reponse classeA = creerClasse("TLE", "S2", "TS2B");
    Reponse salleA = post(SECRETARIAT_A, A, SALLES, corps("nom", "Salle A", "capacite", 40));
    UUID anneeB = creerEtOuvrirAnnee(DIRECTEUR_B, B);
    UUID tleB = niveau(DIRECTEUR_B, B, "TLE");
    UUID s2B = serie(DIRECTEUR_B, B, "S2");
    Reponse classeB =
        post(
            DIRECTEUR_B,
            B,
            CLASSES,
            corps(
                "anneeId",
                anneeB,
                "niveauId",
                tleB,
                "serieId",
                s2B,
                "nom",
                "TS2B",
                "capacite",
                30));
    assertThat(classeB.statut()).isEqualTo(201); // même nom, autre école : pas de conflit

    assertThat(noms(get(DIRECTEUR_B, B, CLASSES))).containsExactly("TS2B");
    assertThat(get(DIRECTEUR_B, B, CLASSES).corps().get(0).path("id").asText())
        .isEqualTo(classeB.id().toString());
    assertThat(get(DIRECTEUR_B, B, CLASSES + "/" + classeA.id()).statut()).isEqualTo(404);
    assertThat(
            put(
                    DIRECTEUR_B,
                    B,
                    CLASSES + "/" + classeA.id(),
                    classeA.version(),
                    corps("niveauId", tleB, "serieId", s2B, "nom", "HACK", "capacite", 1))
                .statut())
        .isEqualTo(404);
    assertThat(delete(DIRECTEUR_B, B, CLASSES + "/" + classeA.id()).statut()).isEqualTo(404);
    assertThat(get(DIRECTEUR_B, B, SALLES + "/" + salleA.id()).statut()).isEqualTo(404);
    assertThat(delete(DIRECTEUR_B, B, SALLES + "/" + salleA.id()).statut()).isEqualTo(404);
    assertThat(
            put(
                    DIRECTEUR_B,
                    B,
                    SALLES + "/" + salleA.id(),
                    salleA.version(),
                    corps("nom", "HACK", "capacite", 1))
                .statut())
        .isEqualTo(404);
    assertThat(get(DIRECTEUR_B, B, SALLES).corps()).isEmpty();

    // Classe de B qui désigne une salle, une année ou un niveau de A : introuvables côté B.
    assertThat(
            post(
                    DIRECTEUR_B,
                    B,
                    CLASSES,
                    corps(
                        "anneeId",
                        anneeB,
                        "niveauId",
                        tleB,
                        "serieId",
                        s2B,
                        "nom",
                        "TS2C",
                        "capacite",
                        30,
                        "salleId",
                        salleA.id()))
                .code())
        .isEqualTo("SALLE_INTROUVABLE");
    assertThat(
            post(
                    DIRECTEUR_B,
                    B,
                    CLASSES,
                    corps(
                        "anneeId",
                        annee,
                        "niveauId",
                        tleB,
                        "serieId",
                        s2B,
                        "nom",
                        "TS2C",
                        "capacite",
                        30))
                .code())
        .isEqualTo("ANNEE_INTROUVABLE");
    assertThat(
            post(
                    DIRECTEUR_B,
                    B,
                    CLASSES,
                    corps(
                        "anneeId",
                        anneeB,
                        "niveauId",
                        niveau(DIRECTEUR_A, A, "TLE"),
                        "serieId",
                        s2B,
                        "nom",
                        "TS2C",
                        "capacite",
                        30))
                .code())
        .isEqualTo("NIVEAU_INTROUVABLE");

    assertThat(noms(get(DIRECTEUR_A, A, CLASSES))).containsExactly("TS2B");
    assertThat(get(DIRECTEUR_A, A, CLASSES + "/" + classeA.id()).corps().path("capacite").asInt())
        .isEqualTo(45);
  }
}
