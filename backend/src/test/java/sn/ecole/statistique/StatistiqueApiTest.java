package sn.ecole.statistique;

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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sn.ecole.inscription.TestInscription;
import tools.jackson.databind.JsonNode;

/**
 * Tableau de bord de la direction : réservé au directeur, isolé par école, agrégats exacts, cache
 * de cinq minutes par école. Toutes les identités sont fictives.
 */
class StatistiqueApiTest extends TestInscription {

  private static final String DIRECTION = "/api/v1/statistiques/direction";
  private static final LocalDate AUJOURDHUI = LocalDate.now(ZoneId.of("Africa/Dakar"));

  private UUID annee;
  private UUID classeCp;
  private UUID classe6e;

  @BeforeEach
  void structure() throws Exception {
    annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    classeCp = creerClasse(DIRECTEUR_A, A, annee, "CP", "CP A");
    classe6e = creerClasse(DIRECTEUR_A, A, annee, "6E", "6E A");
  }

  private UUID creerClasse(UUID qui, UUID ecole, UUID an, String niveau, String nom)
      throws Exception {
    Reponse r =
        post(
            qui,
            ecole,
            "/api/v1/classes",
            corps(
                "anneeId", an, "niveauId", niveau(qui, ecole, niveau), "nom", nom, "capacite", 40));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  private UUID inscrire(String prenoms, UUID classe, String statut) throws Exception {
    UUID eleve = creerEleve(A, prenoms);
    Reponse r =
        post(
            SECRETARIAT_A,
            A,
            "/api/v1/inscriptions",
            corps(
                "eleveId",
                eleve,
                "classeId",
                classe,
                "statut",
                statut,
                "redoublant",
                false,
                "optionIds",
                List.of()));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return eleve;
  }

  private void deposerPiece(UUID eleve, String type, String statut) {
    superUtilisateur()
        .update(
            """
            INSERT INTO ecole.piece_dossier
              (id, tenant_id, eleve_id, type, cle, content_type, taille, empreinte_sha256, statut)
            VALUES (?, ?, ?, ?, 'cle-fictive', 'application/pdf', 1, ?, ?)""",
            UUID.randomUUID(),
            A,
            eleve,
            type,
            "0".repeat(64),
            statut);
  }

  private JsonNode direction(String parametres) throws Exception {
    Reponse r = get(DIRECTEUR_A, A, DIRECTION + parametres);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    return r.corps();
  }

  private static long nombre(JsonNode liste, String champ, String valeur) {
    for (JsonNode n : liste) {
      if (valeur.equals(n.path(champ).asText())) {
        return n.path("nombre").asLong();
      }
    }
    throw new AssertionError(champ + "=" + valeur + " absent de " + liste);
  }

  // --- Droits ---

  @Test
  void seulLeDirecteurVoitLeTableauDeBord() throws Exception {
    Map<String, UUID> refuses = new LinkedHashMap<>();
    refuses.put("SECRETARIAT", SECRETARIAT_A);
    refuses.put("CENSEUR", CENSEUR_A);
    refuses.put("SURVEILLANT", SURVEILLANT_A);
    refuses.put("COMPTABLE", COMPTABLE_A);
    refuses.put("ENSEIGNANT", ENSEIGNANT_A_PARENT_B);
    refuses.put("PARENT", PARENT_A);
    refuses.put("ELEVE", ELEVE_A);

    for (var role : refuses.entrySet()) {
      Reponse r = get(role.getValue(), A, DIRECTION);
      assertThat(r.statut()).as(role.getKey()).isEqualTo(403);
    }
    assertThat(get(DIRECTEUR_A, A, DIRECTION).statut()).isEqualTo(200);
  }

  @Test
  void sansJetonLaRequeteEstRefusee() throws Exception {
    var brut =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(DIRECTION)
                    .header("X-Tenant-Id", A.toString()))
            .andReturn()
            .getResponse();
    assertThat(brut.getStatus()).isEqualTo(401);
  }

  @Test
  void unRefusNeDonneAucunChiffreMemeSiLeCacheEstChaud() throws Exception {
    inscrire("Awa", classeCp, "INSCRIT");
    direction(""); // le cache de l'école est chaud
    Reponse r = get(SECRETARIAT_A, A, DIRECTION);
    assertThat(r.statut()).isEqualTo(403);
    assertThat(r.brut().getContentAsString()).doesNotContain("inscrits");
  }

  @Test
  void lesFiltresInvalidesSontRefuses() throws Exception {
    assertThat(get(DIRECTEUR_A, A, DIRECTION + "?cycle=PRIMAIRE").statut()).isEqualTo(400);
    assertThat(get(DIRECTEUR_A, A, DIRECTION + "?periode=TOUT").statut()).isEqualTo(400);
  }

  // --- Agrégats ---

  @Test
  void effectifsParCycleNiveauEtStatut() throws Exception {
    inscrire("Awa", classeCp, "INSCRIT");
    inscrire("Binta", classeCp, "INSCRIT");
    inscrire("Coumba", classe6e, "INSCRIT");
    inscrire("Dieynaba", classe6e, "PRE_INSCRIT");

    JsonNode t = direction("");

    assertThat(t.path("anneeId").asText()).isEqualTo(annee.toString());
    assertThat(t.path("anneeLibelle").asText()).isEqualTo("2026-2027");
    assertThat(t.path("inscrits").asLong()).isEqualTo(3);
    assertThat(nombre(t.path("parCycle"), "cycle", "ELEMENTAIRE")).isEqualTo(2);
    assertThat(nombre(t.path("parCycle"), "cycle", "MOYEN")).isEqualTo(1);
    assertThat(nombre(t.path("parCycle"), "cycle", "SECONDAIRE")).isZero();
    assertThat(nombre(t.path("parNiveau"), "code", "CP")).isEqualTo(2);
    assertThat(nombre(t.path("parNiveau"), "code", "6E")).isEqualTo(1);
    assertThat(nombre(t.path("parStatut"), "statut", "INSCRIT")).isEqualTo(3);
    assertThat(nombre(t.path("parStatut"), "statut", "PRE_INSCRIT")).isEqualTo(1);
    assertThat(t.path("parStatut")).hasSize(5);
    // Que des nombres : ni nom, ni prénom, ni identifiant d'élève.
    assertThat(t.toString()).doesNotContain("Awa", "Binta", "Fictif", "eleveId", "matricule");
  }

  @Test
  void leFiltreDeCycleRestreintToutLeTableau() throws Exception {
    inscrire("Awa", classeCp, "INSCRIT");
    inscrire("Coumba", classe6e, "INSCRIT");
    inscrire("Dieynaba", classe6e, "INSCRIT");

    JsonNode t = direction("?cycle=MOYEN");

    assertThat(t.path("cycle").asText()).isEqualTo("MOYEN");
    assertThat(t.path("inscrits").asLong()).isEqualTo(2);
    assertThat(t.path("parCycle")).hasSize(1);
    assertThat(t.path("parNiveau"))
        .allSatisfy(n -> assertThat(n.path("cycle").asText()).isEqualTo("MOYEN"));
    assertThat(t.path("dossiers").path("total").asLong()).isEqualTo(2);
  }

  @Test
  void dossiersCompletsSelonLesPiecesExigeesNonBloquees() throws Exception {
    UUID complet = inscrire("Awa", classeCp, "INSCRIT");
    UUID enAnalyse = inscrire("Binta", classeCp, "INSCRIT");
    UUID infecte = inscrire("Coumba", classeCp, "INSCRIT");
    UUID incomplet = inscrire("Dieynaba", classeCp, "INSCRIT");
    inscrire("Fatou", classeCp, "INSCRIT"); // aucune pièce
    deposerPiece(complet, "ACTE_NAISSANCE", "SAIN");
    deposerPiece(complet, "PHOTO_IDENTITE", "SAIN");
    deposerPiece(enAnalyse, "ACTE_NAISSANCE", "EN_ANALYSE");
    deposerPiece(enAnalyse, "PHOTO_IDENTITE", "SAIN");
    deposerPiece(infecte, "ACTE_NAISSANCE", "SAIN");
    deposerPiece(infecte, "PHOTO_IDENTITE", "INFECTE");
    deposerPiece(incomplet, "ACTE_NAISSANCE", "SAIN");
    deposerPiece(incomplet, "CERTIFICAT", "SAIN"); // n'est pas une pièce exigée

    JsonNode dossiers = direction("").path("dossiers");

    assertThat(dossiers.path("total").asLong()).isEqualTo(5);
    assertThat(dossiers.path("dont").asLong()).isEqualTo(2);
    assertThat(dossiers.path("pourcentage").asInt()).isEqualTo(40);
  }

  @Test
  void famillesConnecteesSeulementAvecUnCompteActiveEtUnAccesEspaceParent() throws Exception {
    UUID awa = inscrire("Awa", classeCp, "INSCRIT");
    UUID binta = inscrire("Binta", classeCp, "INSCRIT");
    UUID active = creerResponsable(SECRETARIAT_A, A, "Mere", "+221700000010");
    UUID attente = creerResponsable(SECRETARIAT_A, A, "Pere", "+221700000011");
    UUID sansAcces = creerResponsable(SECRETARIAT_A, A, "Tuteur", "+221700000012");
    lier(SECRETARIAT_A, A, awa, active, "MERE", true);
    lier(
        SECRETARIAT_A,
        A,
        binta,
        active,
        "MERE",
        true); // une famille, deux enfants : comptée une fois
    lier(SECRETARIAT_A, A, awa, attente, "PERE", false);
    lier(SECRETARIAT_A, A, binta, sansAcces, "TUTEUR", false);
    relierCompteParent(A, active, PARENT_A);
    superUtilisateur()
        .update(
            "UPDATE ecole.lien_parental SET acces_espace_parent = false, recoit_notifications ="
                + " false WHERE responsable_id = ?",
            sansAcces);

    JsonNode familles = direction("").path("familles");

    assertThat(familles.path("total").asLong()).isEqualTo(2);
    assertThat(familles.path("dont").asLong()).isEqualTo(1);
    assertThat(familles.path("pourcentage").asInt()).isEqualTo(50);
  }

  @Test
  void laCourbeCompteLesInscriptionsParSemaineSurLaPeriode() throws Exception {
    UUID awa = inscrire("Awa", classeCp, "INSCRIT");
    inscrire("Binta", classeCp, "INSCRIT");
    inscrire("Coumba", classe6e, "PRE_INSCRIT");
    // Une inscription plus ancienne : il y a trois semaines.
    superUtilisateur()
        .update(
            "UPDATE ecole.inscription SET date_entree = ? WHERE eleve_id = ?",
            AUJOURDHUI.minusWeeks(3),
            awa);

    JsonNode courbe = direction("?periode=JOURS_30").path("courbe");

    assertThat(courbe).isNotEmpty();
    assertThat(courbe)
        .allSatisfy(
            s ->
                assertThat(LocalDate.parse(s.path("lundi").asText()).getDayOfWeek().getValue())
                    .isEqualTo(1));
    long total = 0;
    long cetteSemaine = 0;
    LocalDate lundi =
        AUJOURDHUI.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    for (JsonNode s : courbe) {
      total += s.path("nombre").asLong();
      if (lundi.toString().equals(s.path("lundi").asText())) {
        cetteSemaine = s.path("nombre").asLong();
      }
    }
    assertThat(total).isEqualTo(3);
    assertThat(cetteSemaine).isEqualTo(2);

    // Sur l'année entière, la plage est celle de l'année : elle reste découpée par lundis.
    assertThat(direction("?periode=ANNEE").path("courbe")).isNotEmpty();
  }

  @Test
  void sansAnneeEnCoursLeTableauEstVide() throws Exception {
    // L'école B n'a ouvert aucune année.
    Reponse r = get(DIRECTEUR_B, B, DIRECTION);

    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("anneeId").isNull()).isTrue();
    assertThat(r.corps().path("inscrits").asLong()).isZero();
    assertThat(r.corps().path("parStatut")).hasSize(5);
    assertThat(r.corps().path("courbe")).isEmpty();
  }

  // --- Isolement ---

  @Test
  void lEcoleBNeVoitJamaisLesChiffresDeLEcoleA() throws Exception {
    inscrire("Awa", classeCp, "INSCRIT");
    inscrire("Binta", classe6e, "INSCRIT");
    assertThat(direction("").path("inscrits").asLong()).isEqualTo(2);

    UUID anneeB = creerEtOuvrirAnnee(DIRECTEUR_B, B);
    UUID classeB = creerClasse(DIRECTEUR_B, B, anneeB, "CP", "CP B");
    Reponse eleveB = post(DIRECTEUR_B, B, ELEVES, identite("Eleve", "Fictif-B"));
    assertThat(
            post(
                    DIRECTEUR_B,
                    B,
                    "/api/v1/inscriptions",
                    corps(
                        "eleveId",
                        eleveB.id(),
                        "classeId",
                        classeB,
                        "statut",
                        "INSCRIT",
                        "redoublant",
                        false,
                        "optionIds",
                        List.of()))
                .statut())
        .isEqualTo(201);

    Reponse b = get(DIRECTEUR_B, B, DIRECTION);
    assertThat(b.corps().path("anneeId").asText()).isEqualTo(anneeB.toString());
    assertThat(b.corps().path("inscrits").asLong()).isEqualTo(1);
    // Le cache de A est chaud : B ne le lit pas, et A garde ses propres chiffres.
    assertThat(direction("").path("inscrits").asLong()).isEqualTo(2);
    assertThat(direction("").path("anneeId").asText()).isEqualTo(annee.toString());
  }

  @Test
  void lEcoleNeSeLitPasAvecUnCompteQuiNEnEstPasMembre() throws Exception {
    inscrire("Awa", classeCp, "INSCRIT");
    // Le directeur de B n'a aucun droit sur A, même en désignant A.
    Reponse r = get(DIRECTEUR_B, A, DIRECTION);
    assertThat(r.statut()).isIn(401, 403, 404);
    assertThat(r.brut().getContentAsString()).doesNotContain("inscrits");
  }

  // --- Cache ---

  @Test
  void leSecondAppelVientDuCacheJusquAExpiration() throws Exception {
    inscrire("Awa", classeCp, "INSCRIT");
    JsonNode premier = direction("");
    inscrire("Binta", classeCp, "INSCRIT");

    JsonNode second = direction("");

    assertThat(second.path("calculeLe")).isEqualTo(premier.path("calculeLe"));
    assertThat(second.path("inscrits").asLong()).isEqualTo(1);
    // Un autre filtre est calculé à part, donc à jour.
    assertThat(direction("?cycle=ELEMENTAIRE").path("inscrits").asLong()).isEqualTo(2);
  }
}
