package sn.ecole.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.SECRETARIAT_A;
import static sn.ecole.Fixtures.SURVEILLANT_A;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import sn.ecole.Fixtures;
import sn.ecole.inscription.TestInscription;
import tools.jackson.databind.JsonNode;

/**
 * Affectations du personnel et périmètre qui en découle : un enseignant ne voit que ses classes, un
 * surveillant que ses cycles, sans affectation rien ; une fin d'affectation vaut tout de suite.
 */
class AffectationsApiTest extends TestInscription {

  private static final String AFFECTATIONS = "/api/v1/affectations";
  private static final UUID ENSEIGNANT = ENSEIGNANT_A_PARENT_B;

  /** Second enseignant de l'école A (remplaçant). */
  private static final UUID REMPLACANT = UUID.fromString("01930000-0000-7000-8000-0000000001e2");

  private static final LocalDate RENTREE = LocalDate.of(2026, 10, 5);
  private static final LocalDate JOUR = LocalDate.of(2026, 11, 4);

  private UUID annee;
  private UUID sixiemeA;
  private UUID sixiemeB;
  private UUID ciA;
  private UUID maths;
  private UUID svt;

  @BeforeEach
  void structure() throws Exception {
    horloge.fixer(JOUR);
    Fixtures.appartenance(superUtilisateur(), A, REMPLACANT, "ENSEIGNANT");
    annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    sixiemeA = classe("6E", "6E A");
    sixiemeB = classe("6E", "6E B");
    ciA = classe("CI", "CI A");
    maths = matiere("MATHS");
    svt = matiere("SVT");
  }

  private UUID classe(String niveau, String nom) throws Exception {
    Reponse r =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/classes",
            corps(
                "anneeId",
                annee,
                "niveauId",
                niveau(DIRECTEUR_A, A, niveau),
                "nom",
                nom,
                "capacite",
                40));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  private UUID matiere(String code) throws Exception {
    for (JsonNode m : get(DIRECTEUR_A, A, "/api/v1/matieres").corps()) {
      if (code.equals(m.path("code").asText())) {
        return UUID.fromString(m.path("id").asText());
      }
    }
    throw new AssertionError("Matière introuvable : " + code);
  }

  private Reponse affecter(UUID qui, Map<String, Object> corps) throws Exception {
    return post(qui, A, AFFECTATIONS, corps);
  }

  private Map<String, Object> enseignement(UUID utilisateur, UUID classe, UUID matiere) {
    return corps(
        "type",
        "ENSEIGNEMENT",
        "anneeId",
        annee,
        "utilisateurId",
        utilisateur,
        "classeId",
        classe,
        "matiereId",
        matiere,
        "debut",
        RENTREE.toString());
  }

  private JsonNode affecte(Map<String, Object> corps) throws Exception {
    Reponse r = affecter(DIRECTEUR_A, corps);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.corps().get(0);
  }

  private Reponse terminer(UUID qui, UUID ecole, JsonNode affectation, String fin)
      throws Exception {
    return executer(
        MockMvcRequestBuilders.post(AFFECTATIONS + "/" + affectation.path("id").asText() + "/fin")
            .header("If-Match", "\"" + affectation.path("version").asInt() + "\"")
            .contentType("application/json")
            .content(JSON.writeValueAsString(fin == null ? corps() : corps("fin", fin))),
        qui,
        ecole);
  }

  private void inscrire(UUID eleve, UUID classe) throws Exception {
    Reponse r =
        post(
            SECRETARIAT_A,
            A,
            "/api/v1/inscriptions",
            corps("eleveId", eleve, "classeId", classe, "statut", "INSCRIT", "redoublant", false));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
  }

  private List<String> nomsClasses(UUID qui) throws Exception {
    List<String> noms = new ArrayList<>();
    get(qui, A, "/api/v1/classes?annee=" + annee)
        .corps()
        .forEach(c -> noms.add(c.path("nom").asText()));
    return noms;
  }

  // --- Enseignant ---

  @Test
  void unEnseignantSansAffectationNeVoitAucuneClasse() throws Exception {
    assertThat(nomsClasses(ENSEIGNANT)).isEmpty();
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeA).statut()).isEqualTo(403);
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeA + "/eleves").statut())
        .isEqualTo(403);
    assertThat(get(ENSEIGNANT, A, "/api/v1/moi/affectations").corps()).isEmpty();
  }

  @Test
  void unEnseignantNeLitPasUneClasseNonAffecteeDeSonEcole() throws Exception {
    affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    UUID awa = creerEleve(A, "Awa");
    inscrire(awa, sixiemeA);
    inscrire(creerEleve(A, "Binta"), sixiemeB);

    assertThat(nomsClasses(ENSEIGNANT)).containsExactly("6E A");
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeA).statut()).isEqualTo(200);
    JsonNode eleves = get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeA + "/eleves").corps();
    assertThat(eleves)
        .singleElement()
        .satisfies(e -> assertThat(e.path("id").asText()).isEqualTo(awa.toString()));
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeB).statut()).isEqualTo(403);
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeB + "/eleves").statut())
        .isEqualTo(403);
    // La fiche et la recherche d'élèves restent hors de portée de l'enseignant.
    assertThat(get(ENSEIGNANT, A, ELEVES + "/" + awa).statut()).isEqualTo(403);

    JsonNode miennes = get(ENSEIGNANT, A, "/api/v1/moi/affectations").corps();
    assertThat(miennes)
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.path("classe").asText()).isEqualTo("6E A");
              assertThat(a.path("matiere").asText()).isEqualTo("Mathématiques");
              assertThat(a.path("enCours").asBoolean()).isTrue();
            });
  }

  @Test
  void leTitulaireVoitSaClasseDeLElementaireEtSeulementLElementaire() throws Exception {
    Map<String, Object> titulaireCi =
        corps(
            "type",
            "TITULAIRE",
            "anneeId",
            annee,
            "utilisateurId",
            ENSEIGNANT,
            "classeId",
            ciA,
            "debut",
            RENTREE.toString());
    affecte(titulaireCi);
    assertThat(nomsClasses(ENSEIGNANT)).containsExactly("CI A");

    Map<String, Object> titulaire6e = new java.util.HashMap<>(titulaireCi);
    titulaire6e.put("classeId", sixiemeA);
    assertThat(affecter(DIRECTEUR_A, titulaire6e).code()).isEqualTo("TITULAIRE_ELEMENTAIRE");
    // Un seul maître titulaire à la fois.
    Map<String, Object> autre = new java.util.HashMap<>(titulaireCi);
    autre.put("utilisateurId", REMPLACANT);
    assertThat(affecter(DIRECTEUR_A, autre).code()).isEqualTo("AFFECTATION_EN_CONFLIT");
  }

  @Test
  void lesReglesDAffectationSontControlees() throws Exception {
    // Philosophie : secondaire seulement, pas en 6e.
    assertThat(affecter(DIRECTEUR_A, enseignement(ENSEIGNANT, sixiemeA, matiere("PHILO"))).code())
        .isEqualTo("MATIERE_HORS_CYCLE");
    // Le surveillant n'enseigne pas ; l'enseignant ne surveille pas.
    assertThat(affecter(DIRECTEUR_A, enseignement(SURVEILLANT_A, sixiemeA, maths)).code())
        .isEqualTo("ROLE_REQUIS");
    Map<String, Object> surveillance =
        corps(
            "type",
            "SURVEILLANCE",
            "anneeId",
            annee,
            "utilisateurId",
            ENSEIGNANT,
            "cycles",
            List.of("MOYEN"),
            "debut",
            RENTREE.toString());
    assertThat(affecter(DIRECTEUR_A, surveillance).code()).isEqualTo("ROLE_REQUIS");
    // Avant la rentrée : hors de l'année.
    Map<String, Object> avant = enseignement(ENSEIGNANT, sixiemeA, maths);
    avant.put("debut", "2026-09-01");
    assertThat(affecter(DIRECTEUR_A, avant).code()).isEqualTo("PERIODE_INVALIDE");
    // Une matière dans une classe : une personne à la fois.
    affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    assertThat(affecter(DIRECTEUR_A, enseignement(REMPLACANT, sixiemeA, maths)).code())
        .isEqualTo("AFFECTATION_EN_CONFLIT");
    assertThat(affecter(DIRECTEUR_A, enseignement(REMPLACANT, sixiemeA, svt)).statut())
        .isEqualTo(201);
  }

  // --- Fin, remplacement, historique ---

  @Test
  void laFinDAffectationVautToutDeSuite() throws Exception {
    JsonNode a = affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    // Le périmètre est lu (et mis en cache) avant la fin.
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeA).statut()).isEqualTo(200);

    Reponse fin = terminer(DIRECTEUR_A, A, a, null);
    assertThat(fin.statut()).as(fin.corps().toString()).isEqualTo(204);
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeA).statut()).isEqualTo(403);
    assertThat(nomsClasses(ENSEIGNANT)).isEmpty();
    assertThat(journal("AFFECTATION_TERMINEE")).hasSize(1);
    // Une affectation terminée ne se termine pas deux fois.
    JsonNode relue = get(DIRECTEUR_A, A, AFFECTATIONS + "?annee=" + annee).corps().get(0);
    assertThat(relue.path("fin").asText()).isEqualTo(JOUR.toString());
    assertThat(relue.path("enCours").asBoolean()).isFalse();
    assertThat(terminer(DIRECTEUR_A, A, relue, null).code()).isEqualTo("AFFECTATION_TERMINEE");
  }

  @Test
  void unRemplacementPasseLaClasseAuRemplacantALaDatePrevueEtGardeLHistorique() throws Exception {
    JsonNode titulaire = affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    LocalDate releve = JOUR.plusDays(7);
    assertThat(terminer(DIRECTEUR_A, A, titulaire, releve.toString()).statut()).isEqualTo(204);
    Map<String, Object> remplacement = enseignement(REMPLACANT, sixiemeA, maths);
    remplacement.put("debut", releve.toString());
    affecte(remplacement);

    // Jusqu'à la relève : le titulaire, pas encore le remplaçant.
    assertThat(nomsClasses(ENSEIGNANT)).containsExactly("6E A");
    assertThat(nomsClasses(REMPLACANT)).isEmpty();
    // À la relève : l'inverse, sans autre action.
    horloge.fixer(releve);
    assertThat(nomsClasses(ENSEIGNANT)).isEmpty();
    assertThat(nomsClasses(REMPLACANT)).containsExactly("6E A");

    JsonNode historique = get(CENSEUR_A, A, AFFECTATIONS + "?annee=" + annee).corps();
    assertThat(historique).hasSize(2);
    assertThat(historique.get(0).path("enCours").asBoolean()).isFalse();
    assertThat(historique.get(1).path("enCours").asBoolean()).isTrue();
  }

  @Test
  void uneAffectationQuiNAPasCommenceEstAnnulee() throws Exception {
    Map<String, Object> plusTard = enseignement(ENSEIGNANT, sixiemeA, maths);
    plusTard.put("debut", JOUR.plusDays(10).toString());
    JsonNode a = affecte(plusTard);
    assertThat(terminer(DIRECTEUR_A, A, a, null).statut()).isEqualTo(204);
    assertThat(get(DIRECTEUR_A, A, AFFECTATIONS + "?annee=" + annee).corps()).isEmpty();
    assertThat(journal("AFFECTATION_ANNULEE")).hasSize(1);
  }

  @Test
  void chaqueEcritureEstJournaliseeSansNom() throws Exception {
    JsonNode a = affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    Reponse periode =
        executer(
            MockMvcRequestBuilders.put(AFFECTATIONS + "/" + a.path("id").asText())
                .header("If-Match", "\"" + a.path("version").asInt() + "\"")
                .contentType("application/json")
                .content(
                    JSON.writeValueAsString(
                        corps("debut", RENTREE.plusDays(1).toString(), "fin", "2027-06-30"))),
            CENSEUR_A,
            A);
    assertThat(periode.statut()).as(periode.corps().toString()).isEqualTo(204);
    assertThat(journal("AFFECTATION_CREEE")).hasSize(1);
    assertThat(journal("AFFECTATION_MODIFIEE"))
        .singleElement()
        .satisfies(j -> assertThat(j.get("details").toString()).contains("2027-06-30"));
    JsonNode relue = get(DIRECTEUR_A, A, AFFECTATIONS + "?annee=" + annee).corps().get(0);
    terminer(DIRECTEUR_A, A, relue, null);
    assertThat(journal("AFFECTATION_TERMINEE")).hasSize(1);
    // Identifiants et dates seulement : aucun nom dans les détails.
    assertThat(journal("AFFECTATION_CREEE").get(0).get("details").toString())
        .contains(ENSEIGNANT.toString())
        .doesNotContain("Fictif");
  }

  @Test
  void uneAffectationNeSurvitPasASonAnnee() throws Exception {
    // Sans fin saisie : elle prend fin le lendemain du dernier jour de l'année (2 juillet 2027).
    JsonNode a = affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    assertThat(a.path("fin").asText()).isEqualTo("2027-07-03");
    Map<String, Object> auDela = enseignement(ENSEIGNANT, sixiemeA, svt);
    auDela.put("fin", "2027-09-01");
    assertThat(affecter(DIRECTEUR_A, auDela).code()).isEqualTo("PERIODE_INVALIDE");

    horloge.fixer(LocalDate.of(2027, 7, 2));
    assertThat(nomsClasses(ENSEIGNANT)).containsExactly("6E A");
    // L'année suivante : plus rien, même pour une classe et un élève de l'année passée.
    UUID awa = creerEleve(A, "Awa");
    horloge.fixer(JOUR);
    inscrire(awa, sixiemeA);
    affecte(
        corps(
            "type",
            "SURVEILLANCE",
            "anneeId",
            annee,
            "utilisateurId",
            SURVEILLANT_A,
            "cycles",
            List.of("MOYEN"),
            "debut",
            RENTREE.toString()));
    horloge.fixer(LocalDate.of(2027, 7, 3));
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeA).statut()).isEqualTo(403);
    assertThat(get(SURVEILLANT_A, A, ELEVES + "/" + awa).statut()).isEqualTo(403);
  }

  @Test
  void desAffectationsSimultaneesNePrennentPasDeuxFoisLaMemePlace() throws Exception {
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(6);
    try {
      List<java.util.concurrent.Future<Reponse>> envois = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        UUID qui = i % 2 == 0 ? ENSEIGNANT : REMPLACANT;
        envois.add(pool.submit(() -> affecter(DIRECTEUR_A, enseignement(qui, sixiemeA, maths))));
      }
      int acceptees = 0;
      for (var envoi : envois) {
        Reponse r = envoi.get();
        if (r.statut() == 201) {
          acceptees++;
        } else {
          assertThat(r.code()).isEqualTo("AFFECTATION_EN_CONFLIT");
        }
      }
      assertThat(acceptees).isEqualTo(1);
    } finally {
      pool.shutdown();
    }
    assertThat(get(DIRECTEUR_A, A, AFFECTATIONS + "?annee=" + annee).corps()).hasSize(1);
  }

  @Test
  void uneFinAvantLeDebutDUneAffectationCommenceeEstRefuseeSansRienEffacer() throws Exception {
    JsonNode a = affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    Reponse fin = terminer(DIRECTEUR_A, A, a, RENTREE.minusDays(1).toString());
    assertThat(fin.code()).isEqualTo("PERIODE_INVALIDE");
    assertThat(get(DIRECTEUR_A, A, AFFECTATIONS + "?annee=" + annee).corps()).hasSize(1);
    assertThat(nomsClasses(ENSEIGNANT)).containsExactly("6E A");
  }

  @Test
  void lEcoleBNeModifiePasLaPeriodeDUneAffectationDeLEcoleA() throws Exception {
    JsonNode a = affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    Reponse r =
        executer(
            MockMvcRequestBuilders.put(AFFECTATIONS + "/" + a.path("id").asText())
                .header("If-Match", "\"" + a.path("version").asInt() + "\"")
                .contentType("application/json")
                .content(
                    JSON.writeValueAsString(
                        corps("debut", RENTREE.toString(), "fin", "2026-11-05"))),
            DIRECTEUR_B,
            B);
    assertThat(r.statut()).isEqualTo(404);
    assertThat(nomsClasses(ENSEIGNANT)).containsExactly("6E A");
  }

  /** Statut, code, titre et message : tout ce qui pourrait trahir l'existence d'un identifiant. */
  private static String empreinte(Reponse r) {
    return r.statut()
        + " | "
        + r.code()
        + " | "
        + r.corps().path("title").asText()
        + " | "
        + r.corps().path("detail").asText();
  }

  @Test
  void horsPerimetreLaReponseNeTrahitPasLExistenceDUnIdentifiant() throws Exception {
    // Enseignant : 6E A seulement. Surveillant : l'élémentaire seulement (6E B lui est étrangère).
    affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    affecte(
        corps(
            "type",
            "SURVEILLANCE",
            "anneeId",
            annee,
            "utilisateurId",
            SURVEILLANT_A,
            "cycles",
            List.of("ELEMENTAIRE"),
            "debut",
            RENTREE.toString()));
    UUID eleveA = creerEleve(A, "Binta");
    inscrire(eleveA, sixiemeB);
    UUID inscriptionA =
        UUID.fromString(
            get(DIRECTEUR_A, A, ELEVES + "/" + eleveA + "/inscriptions")
                .corps()
                .get(0)
                .path("id")
                .asText());

    // Mêmes objets dans l'école B.
    UUID anneeB = creerEtOuvrirAnnee(DIRECTEUR_B, B);
    Reponse classeB =
        post(
            DIRECTEUR_B,
            B,
            "/api/v1/classes",
            corps(
                "anneeId",
                anneeB,
                "niveauId",
                niveau(DIRECTEUR_B, B, "6E"),
                "nom",
                "6E Z",
                "capacite",
                30));
    assertThat(classeB.statut()).as(classeB.corps().toString()).isEqualTo(201);
    UUID eleveB = creerEleve(B, "Coumba");
    Reponse inscriptionB =
        post(
            DIRECTEUR_B,
            B,
            "/api/v1/inscriptions",
            corps(
                "eleveId",
                eleveB,
                "classeId",
                classeB.id(),
                "statut",
                "INSCRIT",
                "redoublant",
                false));
    assertThat(inscriptionB.statut()).as(inscriptionB.corps().toString()).isEqualTo(201);

    UUID inexistant = UUID.fromString("01930000-0000-7000-8000-00000000dead");
    // Pour chaque modèle d'URL : identifiant inexistant, hors périmètre dans l'école, autre école.
    Map<String, List<UUID>> cas =
        Map.of(
            "/api/v1/classes/%s",
            List.of(inexistant, sixiemeB, classeB.id()),
            "/api/v1/classes/%s/options",
            List.of(inexistant, sixiemeB, classeB.id()),
            "/api/v1/classes/%s/eleves",
            List.of(inexistant, sixiemeB, classeB.id()),
            ELEVES + "/%s",
            List.of(inexistant, eleveA, eleveB),
            ELEVES + "/%s/inscriptions",
            List.of(inexistant, eleveA, eleveB),
            "/api/v1/inscriptions/%s",
            List.of(inexistant, inscriptionA, inscriptionB.id()));
    for (UUID qui : List.of(ENSEIGNANT, SURVEILLANT_A)) {
      for (var modele : cas.entrySet()) {
        List<String> empreintes = new ArrayList<>();
        for (UUID id : modele.getValue()) {
          empreintes.add(empreinte(get(qui, A, String.format(modele.getKey(), id))));
        }
        String libelle = qui.equals(ENSEIGNANT) ? "enseignant" : "surveillant";
        if (modele.getKey().startsWith(ELEVES)
            || modele.getKey().startsWith("/api/v1/inscriptions")) {
          if (qui.equals(ENSEIGNANT)) {
            // L'enseignant n'a pas ces lectures par rôle : refus par rôle, identique lui aussi.
            assertThat(empreintes)
                .as("%s %s", libelle, modele.getKey())
                .allMatch(e -> e.startsWith("403 | ACCES_REFUSE"));
            assertThat(empreintes)
                .as("%s %s", libelle, modele.getKey())
                .containsOnly(empreintes.get(0));
            continue;
          }
        }
        assertThat(empreintes.get(0))
            .as("%s %s", libelle, modele.getKey())
            .startsWith("403 | HORS_PERIMETRE |");
        assertThat(empreintes)
            .as("%s %s : inexistant, hors périmètre, autre école", libelle, modele.getKey())
            .containsOnly(empreintes.get(0));
      }
    }
  }

  // --- Surveillant ---

  @Test
  void unSurveillantNeLitPasUnCycleNonAffecte() throws Exception {
    UUID awa = creerEleve(A, "Awa");
    inscrire(awa, sixiemeA);
    UUID ibou = creerEleve(A, "Ibou");
    inscrire(ibou, ciA);
    // Sans affectation : rien.
    assertThat(get(SURVEILLANT_A, A, ELEVES).corps().path("elements")).isEmpty();
    assertThat(get(SURVEILLANT_A, A, ELEVES + "/" + awa).statut()).isEqualTo(403);

    affecte(
        corps(
            "type",
            "SURVEILLANCE",
            "anneeId",
            annee,
            "utilisateurId",
            SURVEILLANT_A,
            "cycles",
            List.of("MOYEN"),
            "debut",
            RENTREE.toString()));

    JsonNode liste = get(SURVEILLANT_A, A, ELEVES).corps().path("elements");
    assertThat(liste)
        .singleElement()
        .satisfies(e -> assertThat(e.path("id").asText()).isEqualTo(awa.toString()));
    assertThat(get(SURVEILLANT_A, A, ELEVES + "/" + awa).statut()).isEqualTo(200);
    assertThat(get(SURVEILLANT_A, A, ELEVES + "/" + ibou).statut()).isEqualTo(403);
    assertThat(get(SURVEILLANT_A, A, ELEVES + "?classe=" + ciA).statut()).isEqualTo(403);
    assertThat(get(SURVEILLANT_A, A, "/api/v1/eleves/" + ibou + "/inscriptions").statut())
        .isEqualTo(403);
    assertThat(nomsClasses(SURVEILLANT_A)).containsExactly("6E A", "6E B");
    JsonNode remplissage =
        get(SURVEILLANT_A, A, "/api/v1/annees/" + annee + "/remplissage-classes").corps();
    assertThat(remplissage).hasSize(2);
  }

  @Test
  void unSurveillantDeTousLesCyclesVoitToutesLesClasses() throws Exception {
    Reponse r =
        affecter(
            CENSEUR_A,
            corps(
                "type",
                "SURVEILLANCE",
                "anneeId",
                annee,
                "utilisateurId",
                SURVEILLANT_A,
                "cycles",
                List.of("ELEMENTAIRE", "MOYEN", "SECONDAIRE"),
                "debut",
                RENTREE.toString()));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    assertThat(r.corps()).hasSize(3);
    assertThat(nomsClasses(SURVEILLANT_A)).containsExactly("6E A", "6E B", "CI A");
  }

  // --- Droits et isolement ---

  @Test
  void seulsLaDirectionEtLeCenseurAffectent() throws Exception {
    for (UUID qui : List.of(SECRETARIAT_A, SURVEILLANT_A, ENSEIGNANT)) {
      assertThat(affecter(qui, enseignement(ENSEIGNANT, sixiemeA, maths)).statut()).isEqualTo(403);
      assertThat(get(qui, A, AFFECTATIONS + "?annee=" + annee).statut()).isEqualTo(403);
      assertThat(get(qui, A, "/api/v1/personnel/affectables").statut()).isEqualTo(403);
    }
    assertThat(get(SECRETARIAT_A, A, "/api/v1/moi/affectations").statut()).isEqualTo(403);
    assertThat(affecter(CENSEUR_A, enseignement(ENSEIGNANT, sixiemeA, maths)).statut())
        .isEqualTo(201);
  }

  @Test
  void lEcoleBNeVoitNiNeTermineLesAffectationsDeLEcoleA() throws Exception {
    JsonNode a = affecte(enseignement(ENSEIGNANT, sixiemeA, maths));
    assertThat(get(DIRECTEUR_B, B, AFFECTATIONS + "?annee=" + annee).statut()).isEqualTo(404);
    assertThat(terminer(DIRECTEUR_B, B, a, null).statut()).isEqualTo(404);
    UUID anneeB = creerEtOuvrirAnnee(DIRECTEUR_B, B);
    Map<String, Object> pirate = enseignement(ENSEIGNANT, sixiemeA, maths);
    pirate.put("anneeId", anneeB);
    assertThat(post(DIRECTEUR_B, B, AFFECTATIONS, pirate).statut()).isIn(400, 404);
    // L'annuaire de B ne contient pas le personnel de A.
    for (JsonNode m : get(DIRECTEUR_B, B, "/api/v1/personnel/affectables").corps()) {
      assertThat(m.path("utilisateurId").asText()).isNotEqualTo(REMPLACANT.toString());
    }
    // L'affectation de A est intacte et vaut toujours.
    assertThat(get(ENSEIGNANT, A, "/api/v1/classes/" + sixiemeA).statut()).isEqualTo(200);
  }

  @Test
  void lAnnuaireReprendLesNomsDuCompteALaConnexion() throws Exception {
    JsonNode avant = get(DIRECTEUR_A, A, "/api/v1/personnel/affectables").corps();
    assertThat(avant)
        .anySatisfy(
            m -> {
              assertThat(m.path("utilisateurId").asText()).isEqualTo(REMPLACANT.toString());
              assertThat(m.path("nom").isNull() || m.path("nom").isMissingNode()).isTrue();
            });
    mockMvc.perform(
        MockMvcRequestBuilders.get("/api/v1/moi")
            .header("X-Tenant-Id", A.toString())
            .with(
                jwt()
                    .jwt(
                        j ->
                            j.subject(REMPLACANT.toString())
                                .claim("azp", "backoffice")
                                .claim("given_name", "Moussa")
                                .claim("family_name", "Remplaçant Fictif"))));
    JsonNode apres = get(DIRECTEUR_A, A, "/api/v1/personnel/affectables").corps();
    assertThat(apres)
        .anySatisfy(
            m -> {
              assertThat(m.path("utilisateurId").asText()).isEqualTo(REMPLACANT.toString());
              assertThat(m.path("prenoms").asText()).isEqualTo("Moussa");
              assertThat(m.path("nom").asText()).isEqualTo("Remplaçant Fictif");
            });
    // Un changement de nom du compte est journalisé, sans le nom lui-même.
    mockMvc.perform(
        MockMvcRequestBuilders.get("/api/v1/moi")
            .header("X-Tenant-Id", A.toString())
            .with(
                jwt()
                    .jwt(
                        j ->
                            j.subject(REMPLACANT.toString())
                                .claim("azp", "backoffice")
                                .claim("given_name", "Moussa")
                                .claim("family_name", "Autre Nom Fictif"))));
    assertThat(journal("ANNUAIRE_NOM_MODIFIE"))
        .singleElement()
        .satisfies(j -> assertThat(j.get("details").toString()).doesNotContain("Fictif"));
    // Le surveillant figure aussi ; le directeur, non (rien à lui affecter).
    assertThat(apres)
        .anySatisfy(
            m -> assertThat(m.path("utilisateurId").asText()).isEqualTo(SURVEILLANT_A.toString()));
    assertThat(apres)
        .noneSatisfy(
            m -> assertThat(m.path("utilisateurId").asText()).isEqualTo(DIRECTEUR_A.toString()));
  }

  @Test
  void lHorlogeDeTestSeRegleEtSAvance() {
    horloge.fixer(JOUR);
    horloge.avancer(Duration.ofDays(1));
    assertThat(LocalDate.now(horloge)).isEqualTo(JOUR.plusDays(1));
  }
}
