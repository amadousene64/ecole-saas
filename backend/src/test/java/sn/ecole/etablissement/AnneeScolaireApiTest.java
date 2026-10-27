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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tools.jackson.databind.JsonNode;

/** Années scolaires, périodes et calendrier : cycle de vie, règles, lecture seule, droits. */
@RecordApplicationEvents
class AnneeScolaireApiTest extends TestEtablissement {

  private static final String ANNEES = "/api/v1/annees-scolaires";

  @Autowired ApplicationEvents evenements;

  private static Object annee(String libelle, String debut, String fin) {
    return corps("libelle", libelle, "dateDebut", debut, "dateFin", fin);
  }

  private List<JsonNode> periodes(UUID utilisateur, UUID ecole, UUID annee) throws Exception {
    List<JsonNode> liste = new ArrayList<>();
    get(utilisateur, ecole, ANNEES + "/" + annee + "/periodes").corps().forEach(liste::add);
    return liste;
  }

  private JsonNode periode(UUID annee, String cycleCode, int numero) throws Exception {
    UUID cycle = cycle(DIRECTEUR_A, A, cycleCode);
    return periodes(DIRECTEUR_A, A, annee).stream()
        .filter(p -> p.path("cycleId").asText().equals(cycle.toString()))
        .filter(p -> p.path("numero").asInt() == numero)
        .findFirst()
        .orElseThrow();
  }

  private static Object dates(String libelle, String debut, String fin) {
    return corps("libelle", libelle, "dateDebut", debut, "dateFin", fin);
  }

  // --- Création ---

  @Test
  void laCreationGenereLesPeriodesDeChaqueCycleEtLesFeries() throws Exception {
    Reponse r = post(DIRECTEUR_A, A, ANNEES, annee("2026-2027", "2026-10-05", "2027-07-02"));
    assertThat(r.statut()).isEqualTo(201);
    assertThat(r.corps().path("statut").asText()).isEqualTo("PREPARATION");
    assertThat(r.brut().getHeader("Location")).endsWith(ANNEES + "/" + r.id());

    List<JsonNode> periodes = periodes(DIRECTEUR_A, A, r.id());
    assertThat(periodes).hasSize(3 + 2 + 2);
    JsonNode premiere = periode(r.id(), "ELEMENTAIRE", 1);
    assertThat(premiere.path("libelle").asText()).isEqualTo("1er trimestre");
    assertThat(premiere.path("dateDebut").asText()).isEqualTo("2026-10-05");
    assertThat(periode(r.id(), "ELEMENTAIRE", 3).path("dateFin").asText()).isEqualTo("2027-07-02");
    assertThat(periode(r.id(), "MOYEN", 2).path("libelle").asText()).isEqualTo("2e semestre");
    assertThat(periode(r.id(), "SECONDAIRE", 2).path("dateFin").asText()).isEqualTo("2027-07-02");

    JsonNode calendrier = get(DIRECTEUR_A, A, ANNEES + "/" + r.id() + "/calendrier").corps();
    List<String> feries = new ArrayList<>();
    calendrier.forEach(
        e -> feries.add(e.path("libelle").asText() + "@" + e.path("dateDebut").asText()));
    assertThat(feries)
        .contains(
            "Toussaint@2026-11-01",
            "Noël@2026-12-25",
            "Jour de l'An@2027-01-01",
            "Lundi de Pâques@2027-03-29",
            "Ascension@2027-05-06",
            "Lundi de Pentecôte@2027-05-17",
            "Fête de l'Indépendance@2027-04-04");
    calendrier.forEach(e -> assertThat(e.path("type").asText()).isEqualTo("FERIE"));
  }

  @Test
  void leSecretariatCreeUneAnneeMaisPasLesAutresRoles() throws Exception {
    assertThat(
            post(SECRETARIAT_A, A, ANNEES, annee("2026-2027", "2026-10-05", "2027-07-02")).statut())
        .isEqualTo(201);
    for (UUID autre : new UUID[] {CENSEUR_A, ENSEIGNANT_A_PARENT_B, PARENT_A, ELEVE_A}) {
      assertThat(post(autre, A, ANNEES, annee("2027-2028", "2027-10-04", "2028-07-07")).statut())
          .as("par %s", autre)
          .isEqualTo(403);
    }
  }

  @Test
  void leLibelleEtLesDatesSontControles() throws Exception {
    assertThat(post(DIRECTEUR_A, A, ANNEES, annee("2026/2027", "2026-10-05", "2027-07-02")).code())
        .isEqualTo("LIBELLE_ANNEE_INVALIDE");
    assertThat(post(DIRECTEUR_A, A, ANNEES, annee("2026-2028", "2026-10-05", "2028-07-02")).code())
        .isEqualTo("LIBELLE_ANNEE_INVALIDE");
    assertThat(post(DIRECTEUR_A, A, ANNEES, annee("2026-2027", "2025-10-05", "2027-07-02")).code())
        .isEqualTo("DATES_ANNEE_INVALIDES");
    assertThat(post(DIRECTEUR_A, A, ANNEES, annee("2026-2027", "2027-07-02", "2026-10-05")).code())
        .isEqualTo("DATES_ANNEE_INVALIDES");
    assertThat(post(DIRECTEUR_A, A, ANNEES, corps("libelle", "2026-2027")).code())
        .isEqualTo("VALIDATION");
  }

  @Test
  void deuxAnneesNePartagentPasLeMemeLibelle() throws Exception {
    creerAnnee(DIRECTEUR_A, A);
    Reponse r = post(DIRECTEUR_A, A, ANNEES, annee("2026-2027", "2026-10-05", "2027-07-02"));
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("ANNEE_EXISTANTE");
    // L'école B peut avoir la même année.
    assertThat(
            post(DIRECTEUR_B, B, ANNEES, annee("2026-2027", "2026-10-05", "2027-07-02")).statut())
        .isEqualTo(201);
  }

  // --- Ouverture : une seule année en cours ---

  @Test
  void seulLeDirecteurOuvreEtCloture() throws Exception {
    UUID id = creerAnnee(DIRECTEUR_A, A);
    for (UUID autre : new UUID[] {SECRETARIAT_A, CENSEUR_A, ENSEIGNANT_A_PARENT_B}) {
      assertThat(post(autre, A, ANNEES + "/" + id + "/ouverture", null).statut())
          .as("ouverture par %s", autre)
          .isEqualTo(403);
    }
    Reponse ouverte = post(DIRECTEUR_A, A, ANNEES + "/" + id + "/ouverture", null);
    assertThat(ouverte.statut()).isEqualTo(200);
    assertThat(ouverte.corps().path("statut").asText()).isEqualTo("EN_COURS");

    assertThat(post(SECRETARIAT_A, A, ANNEES + "/" + id + "/cloture", null).statut())
        .isEqualTo(403);
    Reponse close = post(DIRECTEUR_A, A, ANNEES + "/" + id + "/cloture", null);
    assertThat(close.statut()).isEqualTo(200);
    assertThat(close.corps().path("statut").asText()).isEqualTo("CLOTUREE");
  }

  @Test
  void uneSeuleAnneeEstEnCoursParEcole() throws Exception {
    UUID premiere = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    Reponse suivante = post(DIRECTEUR_A, A, ANNEES, annee("2027-2028", "2027-10-04", "2028-07-07"));
    Reponse r = post(DIRECTEUR_A, A, ANNEES + "/" + suivante.id() + "/ouverture", null);
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("ANNEE_DEJA_EN_COURS");
    assertThat(get(DIRECTEUR_A, A, ANNEES + "/en-cours").id()).isEqualTo(premiere);

    // Une fois la première clôturée, la suivante peut s'ouvrir.
    post(DIRECTEUR_A, A, ANNEES + "/" + premiere + "/cloture", null);
    assertThat(post(DIRECTEUR_A, A, ANNEES + "/" + suivante.id() + "/ouverture", null).statut())
        .isEqualTo(200);
    // Et l'école B n'est pas concernée par l'année en cours de A.
    assertThat(creerEtOuvrirAnnee(DIRECTEUR_B, B)).isNotNull();
  }

  @Test
  void ouvrirDeuxFoisOuReouvrirUneAnneeClotureeEstRefuse() throws Exception {
    UUID id = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    Reponse encore = post(DIRECTEUR_A, A, ANNEES + "/" + id + "/ouverture", null);
    assertThat(encore.statut()).isEqualTo(409);
    assertThat(encore.code()).isEqualTo("TRANSITION_ANNEE_INTERDITE");
    post(DIRECTEUR_A, A, ANNEES + "/" + id + "/cloture", null);
    assertThat(post(DIRECTEUR_A, A, ANNEES + "/" + id + "/ouverture", null).code())
        .isEqualTo("TRANSITION_ANNEE_INTERDITE");
    assertThat(post(DIRECTEUR_A, A, ANNEES + "/" + id + "/cloture", null).code())
        .isEqualTo("TRANSITION_ANNEE_INTERDITE");
  }

  @Test
  void onNeCloturePasUneAnneeEnPreparation() throws Exception {
    UUID id = creerAnnee(DIRECTEUR_A, A);
    Reponse r = post(DIRECTEUR_A, A, ANNEES + "/" + id + "/cloture", null);
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("TRANSITION_ANNEE_INTERDITE");
  }

  @Test
  void lOuvertureExigeDesPeriodesCompletes() throws Exception {
    UUID id = creerAnnee(DIRECTEUR_A, A);
    // Le secondaire passe en trimestres : les 2 semestres déjà générés ne suffisent plus.
    JsonNode secondaire = get(DIRECTEUR_A, A, "/api/v1/cycles").corps().get(2);
    Reponse cycle =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/cycles/" + secondaire.path("id").asText(),
            secondaire.path("version").asInt(),
            corps(
                "libelle",
                "Secondaire",
                "typePeriode",
                "TRIMESTRE",
                "modeNotation",
                "COEFFICIENT",
                "baseMoyenne",
                20));
    assertThat(cycle.statut()).isEqualTo(200);

    Reponse refusee = post(DIRECTEUR_A, A, ANNEES + "/" + id + "/ouverture", null);
    assertThat(refusee.statut()).isEqualTo(422);
    assertThat(refusee.code()).isEqualTo("PERIODES_INCOMPLETES");

    assertThat(post(SECRETARIAT_A, A, ANNEES + "/" + id + "/periodes/generation", null).statut())
        .isEqualTo(200);
    assertThat(periodes(DIRECTEUR_A, A, id)).hasSize(3 + 2 + 3);
    assertThat(post(DIRECTEUR_A, A, ANNEES + "/" + id + "/ouverture", null).statut())
        .isEqualTo(200);
  }

  @Test
  void lOuvertureEtLaClotureSontPublieesEtJournalisees() throws Exception {
    UUID id = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    post(DIRECTEUR_A, A, ANNEES + "/" + id + "/cloture", null);

    assertThat(evenements.stream(AnneeScolaireOuverte.class))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.tenantId()).isEqualTo(A);
              assertThat(e.anneeId()).isEqualTo(id);
              assertThat(e.libelle()).isEqualTo("2026-2027");
            });
    assertThat(evenements.stream(AnneeScolaireCloturee.class)).hasSize(1);

    List<String> actions =
        superUtilisateur()
            .queryForList(
                "SELECT action FROM ecole.journal_audit WHERE tenant_id = ? AND id_cible = ? ORDER BY cree_le",
                String.class,
                A,
                id);
    assertThat(actions).containsExactly("ANNEE_CREEE", "ANNEE_OUVERTE", "ANNEE_CLOTUREE");
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT count(*) FROM ecole.journal_audit WHERE id_cible = ? AND acteur_id = ?",
                    Integer.class,
                    id,
                    DIRECTEUR_A))
        .isEqualTo(3);
  }

  // --- Année clôturée : lecture seule ---

  @Test
  void uneAnneeClotureeEstEnLectureSeule() throws Exception {
    UUID id = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    UUID cp = niveau(DIRECTEUR_A, A, "CP");
    Reponse classe =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/classes",
            corps("anneeId", id, "niveauId", cp, "nom", "CPA", "capacite", 40));
    JsonNode periode = periode(id, "ELEMENTAIRE", 1);
    JsonNode ferie = get(DIRECTEUR_A, A, ANNEES + "/" + id + "/calendrier").corps().get(0);
    post(DIRECTEUR_A, A, ANNEES + "/" + id + "/cloture", null);
    Reponse annee = get(DIRECTEUR_A, A, ANNEES + "/" + id);

    assertCloturee(
        put(
            DIRECTEUR_A,
            A,
            ANNEES + "/" + id,
            annee.version(),
            annee("2026-2027", "2026-10-06", "2027-07-02")));
    assertCloturee(
        put(
            DIRECTEUR_A,
            A,
            ANNEES + "/" + id + "/periodes/" + periode.path("id").asText(),
            periode.path("version").asInt(),
            dates("1er trimestre", "2026-10-05", "2026-12-01")));
    assertCloturee(
        post(
            DIRECTEUR_A,
            A,
            ANNEES + "/" + id + "/calendrier",
            corps(
                "type",
                "FERIE",
                "libelle",
                "Magal",
                "dateDebut",
                "2026-10-24",
                "dateFin",
                "2026-10-24")));
    assertCloturee(
        put(
            DIRECTEUR_A,
            A,
            ANNEES + "/" + id + "/calendrier/" + ferie.path("id").asText(),
            ferie.path("version").asInt(),
            corps(
                "type",
                "FERIE",
                "libelle",
                "Autre",
                "dateDebut",
                ferie.path("dateDebut").asText(),
                "dateFin",
                ferie.path("dateFin").asText())));
    assertCloturee(
        delete(DIRECTEUR_A, A, ANNEES + "/" + id + "/calendrier/" + ferie.path("id").asText()));
    assertCloturee(
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/classes",
            corps("anneeId", id, "niveauId", cp, "nom", "CPB", "capacite", 40)));
    assertCloturee(
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/classes/" + classe.id(),
            classe.version(),
            corps("niveauId", cp, "nom", "CPA2", "capacite", 41)));
    assertCloturee(delete(DIRECTEUR_A, A, "/api/v1/classes/" + classe.id()));

    Reponse regeneration = post(DIRECTEUR_A, A, ANNEES + "/" + id + "/periodes/generation", null);
    assertThat(regeneration.statut()).isEqualTo(409);
    assertThat(regeneration.code()).isEqualTo("ANNEE_NON_EN_PREPARATION");
    assertThat(delete(DIRECTEUR_A, A, ANNEES + "/" + id).code()).isEqualTo("ANNEE_NON_SUPPRIMABLE");

    // Tout est resté tel quel.
    assertThat(get(DIRECTEUR_A, A, "/api/v1/classes/" + classe.id()).corps().path("nom").asText())
        .isEqualTo("CPA");
    assertThat(periode(id, "ELEMENTAIRE", 1).path("dateFin").asText()).isNotEqualTo("2026-12-01");
  }

  private static void assertCloturee(Reponse r) {
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("ANNEE_CLOTUREE");
  }

  // --- Modification, suppression ---

  @Test
  void lesNouvellesDatesDoiventContenirLesPeriodes() throws Exception {
    Reponse creee = post(DIRECTEUR_A, A, ANNEES, annee("2026-2027", "2026-10-05", "2027-07-02"));
    String url = ANNEES + "/" + creee.id();

    Reponse trop =
        put(DIRECTEUR_A, A, url, creee.version(), annee("2026-2027", "2026-10-05", "2027-06-01"));
    assertThat(trop.statut()).isEqualTo(422);
    assertThat(trop.code()).isEqualTo("PERIODE_HORS_ANNEE");

    Reponse etendue =
        put(SECRETARIAT_A, A, url, creee.version(), annee("2026-2027", "2026-10-01", "2027-07-10"));
    assertThat(etendue.statut()).isEqualTo(200);
    assertThat(etendue.corps().path("dateFin").asText()).isEqualTo("2027-07-10");
  }

  @Test
  void uneAnneeEnPreparationSeSupprimeAvecSesPeriodes() throws Exception {
    UUID id = creerAnnee(DIRECTEUR_A, A);
    assertThat(delete(CENSEUR_A, A, ANNEES + "/" + id).statut()).isEqualTo(403);
    assertThat(delete(SECRETARIAT_A, A, ANNEES + "/" + id).statut()).isEqualTo(204);
    assertThat(get(DIRECTEUR_A, A, ANNEES + "/" + id).statut()).isEqualTo(404);
    assertThat(get(DIRECTEUR_A, A, ANNEES).corps()).isEmpty();
    // Suppression logique : les lignes sont conservées.
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT count(*) FROM ecole.periode WHERE annee_id = ? AND supprime_le IS NOT NULL",
                    Integer.class,
                    id))
        .isEqualTo(7);
    // Le libellé est de nouveau disponible.
    assertThat(creerAnnee(DIRECTEUR_A, A)).isNotEqualTo(id);
  }

  @Test
  void uneAnneeEnCoursOuAvecDesClassesNeSePasSupprime() throws Exception {
    UUID enCours = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    assertThat(delete(DIRECTEUR_A, A, ANNEES + "/" + enCours).code())
        .isEqualTo("ANNEE_NON_SUPPRIMABLE");

    Reponse suivante = post(DIRECTEUR_A, A, ANNEES, annee("2027-2028", "2027-10-04", "2028-07-07"));
    post(
        DIRECTEUR_A,
        A,
        "/api/v1/classes",
        corps(
            "anneeId",
            suivante.id(),
            "niveauId",
            niveau(DIRECTEUR_A, A, "CP"),
            "nom",
            "CPA",
            "capacite",
            30));
    Reponse r = delete(DIRECTEUR_A, A, ANNEES + "/" + suivante.id());
    assertThat(r.statut()).isEqualTo(409);
    assertThat(r.code()).isEqualTo("ANNEE_NON_VIDE");
  }

  // --- Lecture ---

  @Test
  void parentsEtElevesLisentAnneeEnCoursPeriodesEtCalendrier() throws Exception {
    assertThat(get(PARENT_A, A, ANNEES + "/en-cours").code()).isEqualTo("AUCUNE_ANNEE_EN_COURS");
    UUID id = creerEtOuvrirAnnee(DIRECTEUR_A, A);

    for (UUID utilisateur : new UUID[] {PARENT_A, ELEVE_A}) {
      assertThat(get(utilisateur, A, ANNEES + "/en-cours").id()).isEqualTo(id);
      assertThat(get(utilisateur, A, ANNEES).statut()).isEqualTo(200);
      assertThat(get(utilisateur, A, ANNEES + "/" + id + "/periodes").corps()).hasSize(7);
      assertThat(get(utilisateur, A, ANNEES + "/" + id + "/calendrier").statut()).isEqualTo(200);
      assertThat(get(utilisateur, A, "/api/v1/classes").statut()).isEqualTo(403);
      assertThat(get(utilisateur, A, "/api/v1/salles").statut()).isEqualTo(403);
    }
  }

  @Test
  void uneAnneeInconnueDonne404() throws Exception {
    Reponse r = get(DIRECTEUR_A, A, ANNEES + "/" + UUID.randomUUID());
    assertThat(r.statut()).isEqualTo(404);
    assertThat(r.code()).isEqualTo("ANNEE_INTROUVABLE");
    assertThat(get(DIRECTEUR_A, A, ANNEES + "/" + UUID.randomUUID() + "/periodes").statut())
        .isEqualTo(404);
    assertThat(get(DIRECTEUR_A, A, ANNEES + "/" + UUID.randomUUID() + "/calendrier").statut())
        .isEqualTo(404);
  }

  // --- Périodes ---

  @Test
  void lesDatesDUnePeriodeSAjustentSansChevauchement() throws Exception {
    UUID id = creerAnnee(DIRECTEUR_A, A);
    JsonNode t1 = periode(id, "ELEMENTAIRE", 1);
    JsonNode t2 = periode(id, "ELEMENTAIRE", 2);
    String url1 = ANNEES + "/" + id + "/periodes/" + t1.path("id").asText();
    int v1 = t1.path("version").asInt();

    // Raccourcir : un trou apparaît (vacances), c'est permis.
    Reponse ok =
        put(SECRETARIAT_A, A, url1, v1, dates("1er trimestre", "2026-10-05", "2026-12-18"));
    assertThat(ok.statut()).isEqualTo(200);
    assertThat(ok.corps().path("dateFin").asText()).isEqualTo("2026-12-18");

    // Empiéter sur le trimestre suivant : refusé.
    Reponse chevauche =
        put(
            DIRECTEUR_A,
            A,
            url1,
            ok.version(),
            dates("1er trimestre", "2026-10-05", t2.path("dateFin").asText()));
    assertThat(chevauche.statut()).isEqualTo(422);
    assertThat(chevauche.code()).isEqualTo("PERIODES_CHEVAUCHANTES");

    // Hors des dates de l'année, ou dates inversées.
    assertThat(
            put(
                    DIRECTEUR_A,
                    A,
                    url1,
                    ok.version(),
                    dates("1er trimestre", "2026-09-01", "2026-12-18"))
                .code())
        .isEqualTo("PERIODE_HORS_ANNEE");
    assertThat(
            put(
                    DIRECTEUR_A,
                    A,
                    url1,
                    ok.version(),
                    dates("1er trimestre", "2026-12-18", "2026-10-05"))
                .code())
        .isEqualTo("PERIODE_INVALIDE");

    // Version périmée.
    assertThat(
            put(DIRECTEUR_A, A, url1, v1, dates("1er trimestre", "2026-10-05", "2026-12-10"))
                .code())
        .isEqualTo("CONFLIT_VERSION");
    assertThat(
            put(
                    CENSEUR_A,
                    A,
                    url1,
                    ok.version(),
                    dates("1er trimestre", "2026-10-05", "2026-12-10"))
                .statut())
        .isEqualTo(403);
  }

  @Test
  void lesPeriodesDeCyclesDifferentsPeuventSeChevaucher() throws Exception {
    UUID id = creerAnnee(DIRECTEUR_A, A);
    JsonNode t1 = periode(id, "ELEMENTAIRE", 1);
    Reponse r =
        put(
            DIRECTEUR_A,
            A,
            ANNEES + "/" + id + "/periodes/" + t1.path("id").asText(),
            t1.path("version").asInt(),
            dates("1er trimestre", "2026-10-05", "2027-02-01"));
    assertThat(r.statut()).isEqualTo(422); // chevauche le 2e trimestre du même cycle
    Reponse semestre =
        put(
            DIRECTEUR_A,
            A,
            ANNEES + "/" + id + "/periodes/" + periode(id, "MOYEN", 1).path("id").asText(),
            periode(id, "MOYEN", 1).path("version").asInt(),
            dates("1er semestre", "2026-10-05", "2027-01-20"));
    assertThat(semestre.statut()).isEqualTo(200);
  }

  @Test
  void unePeriodeInconnueOuDUneAutreAnneeDonne404() throws Exception {
    UUID a1 = creerAnnee(DIRECTEUR_A, A);
    Reponse a2 = post(DIRECTEUR_A, A, ANNEES, annee("2027-2028", "2027-10-04", "2028-07-07"));
    JsonNode periodeDeA1 = periode(a1, "ELEMENTAIRE", 1);

    Reponse inconnue =
        put(
            DIRECTEUR_A,
            A,
            ANNEES + "/" + a1 + "/periodes/" + UUID.randomUUID(),
            0,
            dates("X", "2026-10-05", "2026-12-01"));
    assertThat(inconnue.statut()).isEqualTo(404);
    assertThat(inconnue.code()).isEqualTo("PERIODE_INTROUVABLE");
    assertThat(
            put(
                    DIRECTEUR_A,
                    A,
                    ANNEES + "/" + a2.id() + "/periodes/" + periodeDeA1.path("id").asText(),
                    periodeDeA1.path("version").asInt(),
                    dates("X", "2027-10-04", "2027-12-01"))
                .statut())
        .isEqualTo(404);
  }

  // --- Calendrier ---

  @Test
  void leCalendrierSeGereParLeSecretariat() throws Exception {
    UUID id = creerAnnee(DIRECTEUR_A, A);
    String url = ANNEES + "/" + id + "/calendrier";

    Reponse magal =
        post(
            SECRETARIAT_A,
            A,
            url,
            corps(
                "type",
                "FERIE",
                "libelle",
                "Magal de Touba",
                "dateDebut",
                "2026-10-24",
                "dateFin",
                "2026-10-24"));
    assertThat(magal.statut()).isEqualTo(201);

    Reponse vacances =
        post(
            SECRETARIAT_A,
            A,
            url,
            corps(
                "type",
                "VACANCES",
                "libelle",
                "Vacances de Noël",
                "dateDebut",
                "2026-12-19",
                "dateFin",
                "2027-01-03"));
    assertThat(vacances.statut()).isEqualTo(201);

    // Un férié dans les vacances est normal ; des vacances qui en chevauchent d'autres, non.
    assertThat(
            post(
                    SECRETARIAT_A,
                    A,
                    url,
                    corps(
                        "type",
                        "FERIE",
                        "libelle",
                        "Noël bis",
                        "dateDebut",
                        "2026-12-25",
                        "dateFin",
                        "2026-12-25"))
                .statut())
        .isEqualTo(201);
    Reponse chevauche =
        post(
            SECRETARIAT_A,
            A,
            url,
            corps(
                "type",
                "VACANCES",
                "libelle",
                "Autres",
                "dateDebut",
                "2027-01-02",
                "dateFin",
                "2027-01-10"));
    assertThat(chevauche.statut()).isEqualTo(422);
    assertThat(chevauche.code()).isEqualTo("VACANCES_CHEVAUCHANTES");

    assertThat(
            post(
                    SECRETARIAT_A,
                    A,
                    url,
                    corps(
                        "type",
                        "FERIE",
                        "libelle",
                        "Hors année",
                        "dateDebut",
                        "2025-01-01",
                        "dateFin",
                        "2025-01-01"))
                .code())
        .isEqualTo("EVENEMENT_HORS_ANNEE");
    assertThat(
            post(
                    SECRETARIAT_A,
                    A,
                    url,
                    corps(
                        "type",
                        "VACANCES",
                        "libelle",
                        "Inversées",
                        "dateDebut",
                        "2027-02-10",
                        "dateFin",
                        "2027-02-01"))
                .code())
        .isEqualTo("EVENEMENT_INVALIDE");

    Reponse modifiee =
        put(
            DIRECTEUR_A,
            A,
            url + "/" + vacances.id(),
            vacances.version(),
            corps(
                "type",
                "VACANCES",
                "libelle",
                "Vacances de fin d'année",
                "dateDebut",
                "2026-12-20",
                "dateFin",
                "2027-01-03"));
    assertThat(modifiee.statut()).isEqualTo(200);
    assertThat(modifiee.corps().path("libelle").asText()).isEqualTo("Vacances de fin d'année");
    // Se modifier soi-même ne compte pas comme un chevauchement.
    assertThat(
            put(
                    DIRECTEUR_A,
                    A,
                    url + "/" + vacances.id(),
                    modifiee.version(),
                    corps(
                        "type",
                        "VACANCES",
                        "libelle",
                        "V",
                        "dateDebut",
                        "2026-12-21",
                        "dateFin",
                        "2027-01-02"))
                .statut())
        .isEqualTo(200);

    assertThat(delete(SECRETARIAT_A, A, url + "/" + magal.id()).statut()).isEqualTo(204);
    Reponse introuvable = delete(SECRETARIAT_A, A, url + "/" + magal.id());
    assertThat(introuvable.statut()).isEqualTo(404);
    assertThat(introuvable.code()).isEqualTo("EVENEMENT_INTROUVABLE");

    assertThat(
            post(
                    PARENT_A,
                    A,
                    url,
                    corps(
                        "type",
                        "FERIE",
                        "libelle",
                        "x",
                        "dateDebut",
                        "2026-11-01",
                        "dateFin",
                        "2026-11-01"))
                .statut())
        .isEqualTo(403);
    assertThat(get(PARENT_A, A, url).statut()).isEqualTo(200);
  }

  // --- Isolement ---

  @Test
  void lEcoleBNeVoitNiNeModifieLesAnneesDeLEcoleA() throws Exception {
    UUID anneeA = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    UUID anneeB = creerAnnee(DIRECTEUR_B, B);
    JsonNode periodeA = periode(anneeA, "MOYEN", 1);

    assertThat(get(DIRECTEUR_B, B, ANNEES).corps()).hasSize(1);
    assertThat(get(DIRECTEUR_B, B, ANNEES + "/" + anneeA).statut()).isEqualTo(404);
    assertThat(get(DIRECTEUR_B, B, ANNEES + "/en-cours").code()).isEqualTo("AUCUNE_ANNEE_EN_COURS");
    assertThat(get(DIRECTEUR_B, B, ANNEES + "/" + anneeA + "/periodes").statut()).isEqualTo(404);
    assertThat(
            put(
                    DIRECTEUR_B,
                    B,
                    ANNEES + "/" + anneeA,
                    0,
                    annee("2026-2027", "2026-10-05", "2027-07-02"))
                .statut())
        .isEqualTo(404);
    assertThat(delete(DIRECTEUR_B, B, ANNEES + "/" + anneeA).statut()).isEqualTo(404);
    assertThat(post(DIRECTEUR_B, B, ANNEES + "/" + anneeA + "/ouverture", null).statut())
        .isEqualTo(404);
    assertThat(post(DIRECTEUR_B, B, ANNEES + "/" + anneeA + "/cloture", null).statut())
        .isEqualTo(404);
    assertThat(post(DIRECTEUR_B, B, ANNEES + "/" + anneeA + "/periodes/generation", null).statut())
        .isEqualTo(404);
    assertThat(
            put(
                    DIRECTEUR_B,
                    B,
                    ANNEES + "/" + anneeA + "/periodes/" + periodeA.path("id").asText(),
                    0,
                    dates("X", "2026-10-05", "2026-12-01"))
                .statut())
        .isEqualTo(404);
    assertThat(
            post(
                    DIRECTEUR_B,
                    B,
                    ANNEES + "/" + anneeA + "/calendrier",
                    corps(
                        "type",
                        "FERIE",
                        "libelle",
                        "x",
                        "dateDebut",
                        "2026-11-01",
                        "dateFin",
                        "2026-11-01"))
                .statut())
        .isEqualTo(404);

    assertThat(get(DIRECTEUR_A, A, ANNEES + "/" + anneeA).corps().path("statut").asText())
        .isEqualTo("EN_COURS");
    assertThat(get(DIRECTEUR_B, B, ANNEES + "/" + anneeB).corps().path("statut").asText())
        .isEqualTo("PREPARATION");
  }
}
