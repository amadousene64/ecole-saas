package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.COMPTABLE_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.PARENT_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;
import static sn.ecole.Fixtures.SURVEILLANT_A;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/**
 * Liste des élèves du personnel : statut de l'inscription la plus récente (et filtre), état du
 * dossier au regard des pièces exigées, droits, isolement entre écoles.
 */
class ListeElevesApiTest extends TestInscription {

  private static final String INSCRIPTIONS = "/api/v1/inscriptions";

  private UUID classe;
  private UUID awa;
  private UUID bintou;
  private UUID coumba;

  @BeforeEach
  void structure() throws Exception {
    superUtilisateur().execute("TRUNCATE ecole.outbox_event");
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    Reponse c =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/classes",
            corps(
                "anneeId",
                annee,
                "niveauId",
                niveau(DIRECTEUR_A, A, "6E"),
                "nom",
                "6E A",
                "capacite",
                30));
    assertThat(c.statut()).as(c.corps().toString()).isEqualTo(201);
    classe = c.id();
    awa = creerEleve(A, "Awa");
    bintou = creerEleve(A, "Bintou");
    coumba = creerEleve(A, "Coumba");
  }

  private UUID inscrire(UUID eleve, String statut) throws Exception {
    Reponse r =
        post(
            SECRETARIAT_A,
            A,
            INSCRIPTIONS,
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
    return r.id();
  }

  private void sortir(UUID inscription, String statut) throws Exception {
    Reponse lue = get(DIRECTEUR_A, A, INSCRIPTIONS + "/" + inscription);
    Reponse r =
        executer(
            MockMvcRequestBuilders.post(INSCRIPTIONS + "/" + inscription + "/sortie")
                .header("If-Match", lue.etag())
                .contentType("application/json")
                .content(
                    JSON.writeValueAsString(
                        Map.of("statut", statut, "date", LocalDate.now().toString()))),
            DIRECTEUR_A,
            A);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
  }

  private Reponse deposer(UUID eleve, String type) throws Exception {
    byte[] pdf = ("%PDF-1.4\n% fictif\n" + type + "\n%%EOF").getBytes(StandardCharsets.US_ASCII);
    Reponse r =
        executer(
            MockMvcRequestBuilders.multipart(HttpMethod.POST, ELEVES + "/" + eleve + "/pieces")
                .file(new MockMultipartFile("fichier", "p.pdf", "application/pdf", pdf))
                .file(new MockMultipartFile("type", "", "text/plain", type.getBytes())),
            SECRETARIAT_A,
            A);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r;
  }

  private void statutPiece(UUID piece, String statut) {
    superUtilisateur()
        .update("UPDATE ecole.piece_dossier SET statut = ? WHERE id = ?", statut, piece);
  }

  private static JsonNode ligne(Reponse liste, UUID eleve) {
    for (JsonNode n : liste.corps().path("elements")) {
      if (eleve.toString().equals(n.path("id").asText())) {
        return n;
      }
    }
    throw new AssertionError("Élève absent de la liste : " + eleve);
  }

  private static boolean absent(JsonNode champ) {
    return champ.isMissingNode() || champ.isNull();
  }

  private static List<String> manquantes(JsonNode ligne) {
    List<String> types = new ArrayList<>();
    ligne.path("dossier").path("manquantes").forEach(t -> types.add(t.asText()));
    return types;
  }

  @Test
  void laListeDonneLeStatutDeLInscriptionLaPlusRecenteEtFiltreParStatut() throws Exception {
    UUID inscriptionAwa = inscrire(awa, "INSCRIT");
    inscrire(bintou, "PRE_INSCRIT");

    Reponse liste = get(SECRETARIAT_A, A, ELEVES);
    assertThat(ligne(liste, awa).path("statut").asText()).isEqualTo("INSCRIT");
    assertThat(ligne(liste, awa).path("classe").asText()).isEqualTo("6E A");
    assertThat(ligne(liste, bintou).path("statut").asText()).isEqualTo("PRE_INSCRIT");
    // Jamais inscrit : ni statut ni classe.
    assertThat(absent(ligne(liste, coumba).path("statut"))).isTrue();

    Reponse inscrits = get(SECRETARIAT_A, A, ELEVES + "?statut=INSCRIT");
    assertThat(inscrits.corps().path("elements")).hasSize(1);
    assertThat(inscrits.corps().path("elements").get(0).path("id").asText())
        .isEqualTo(awa.toString());
    assertThat(get(SECRETARIAT_A, A, ELEVES + "?statut=PRE_INSCRIT").corps().path("elements"))
        .hasSize(1);

    // Après la sortie, l'élève n'a plus de classe mais garde le statut de sa dernière inscription.
    sortir(inscriptionAwa, "TRANSFERE");
    Reponse apres = get(SECRETARIAT_A, A, ELEVES);
    assertThat(ligne(apres, awa).path("statut").asText()).isEqualTo("TRANSFERE");
    assertThat(absent(ligne(apres, awa).path("classe"))).isTrue();
    assertThat(get(SECRETARIAT_A, A, ELEVES + "?statut=INSCRIT").corps().path("elements"))
        .isEmpty();
    assertThat(get(SECRETARIAT_A, A, ELEVES + "?statut=TRANSFERE").corps().path("elements"))
        .hasSize(1);
    // Le filtre se combine avec la classe.
    assertThat(
            get(SECRETARIAT_A, A, ELEVES + "?statut=PRE_INSCRIT&classe=" + classe)
                .corps()
                .path("elements"))
        .hasSize(1);
  }

  @Test
  void unStatutInconnuEstRefuse() throws Exception {
    assertThat(get(SECRETARIAT_A, A, ELEVES + "?statut=PAS_UN_STATUT").statut()).isEqualTo(400);
  }

  @Test
  void leDossierDitCeQuiManqueCeQuiEstEnAnalyseEtCeQuiEstRefuse() throws Exception {
    JsonNode vide = ligne(get(SECRETARIAT_A, A, ELEVES), awa);
    assertThat(manquantes(vide)).containsExactlyInAnyOrder("ACTE_NAISSANCE", "PHOTO_IDENTITE");
    assertThat(vide.path("dossier").path("enAnalyse").asInt()).isZero();

    Reponse acte = deposer(awa, "ACTE_NAISSANCE");
    JsonNode enAnalyse = ligne(get(SECRETARIAT_A, A, ELEVES), awa);
    assertThat(manquantes(enAnalyse)).containsExactly("PHOTO_IDENTITE");
    assertThat(enAnalyse.path("dossier").path("enAnalyse").asInt()).isEqualTo(1);

    statutPiece(acte.id(), "SAIN");
    Reponse photo = deposer(awa, "PHOTO_IDENTITE");
    statutPiece(photo.id(), "INFECTE");
    JsonNode refusee = ligne(get(SECRETARIAT_A, A, ELEVES), awa);
    // Une pièce bloquée par l'antivirus ne compte pas comme fournie.
    assertThat(manquantes(refusee)).containsExactly("PHOTO_IDENTITE");
    assertThat(refusee.path("dossier").path("refusees").asInt()).isEqualTo(1);
    assertThat(refusee.path("dossier").path("enAnalyse").asInt()).isZero();

    Reponse bonne = deposer(awa, "PHOTO_IDENTITE");
    statutPiece(bonne.id(), "SAIN");
    assertThat(manquantes(ligne(get(SECRETARIAT_A, A, ELEVES), awa))).isEmpty();
  }

  @Test
  void leCertificatMedicalNeChangeRienAuDossierDeLaListe() throws Exception {
    deposer(awa, "CERTIFICAT_MEDICAL");
    JsonNode l = ligne(get(CENSEUR_A, A, ELEVES), awa);
    assertThat(manquantes(l)).containsExactlyInAnyOrder("ACTE_NAISSANCE", "PHOTO_IDENTITE");
    assertThat(l.path("dossier").path("enAnalyse").asInt()).isZero();
    assertThat(l.path("dossier").path("refusees").asInt()).isZero();
  }

  @Test
  void seulsLesRolesQuiLisentLesPiecesVoientLEtatDuDossier() throws Exception {
    for (UUID qui : List.of(DIRECTEUR_A, SECRETARIAT_A, CENSEUR_A)) {
      Reponse r = get(qui, A, ELEVES);
      assertThat(r.statut()).isEqualTo(200);
      assertThat(ligne(r, awa).path("dossier").isObject()).as("dossier visible").isTrue();
    }
    for (UUID qui : List.of(SURVEILLANT_A, COMPTABLE_A)) {
      Reponse r = get(qui, A, ELEVES);
      assertThat(r.statut()).isEqualTo(200);
      JsonNode l = ligne(r, awa);
      assertThat(absent(l.path("dossier"))).as("dossier masqué").isTrue();
      // Le reste de la ligne (identité, statut) reste lisible.
      assertThat(l.path("matricule").asText()).isNotBlank();
    }
    // Un parent n'a pas accès à la liste du personnel.
    assertThat(get(PARENT_A, A, ELEVES).statut()).isEqualTo(403);
  }

  @Test
  void lEcoleANeVoitPasLesElevesNiLesDossiersDeLEcoleBEtInversement() throws Exception {
    inscrire(awa, "INSCRIT");
    deposer(awa, "ACTE_NAISSANCE");
    UUID elodie = creerEleve(B, "Elodie");

    Reponse deB = get(DIRECTEUR_B, B, ELEVES + "?statut=INSCRIT");
    assertThat(deB.statut()).isEqualTo(200);
    assertThat(deB.corps().path("elements")).isEmpty();
    Reponse toutB = get(DIRECTEUR_B, B, ELEVES);
    assertThat(toutB.corps().path("elements")).hasSize(1);
    JsonNode l = ligne(toutB, elodie);
    // Le dossier de B ignore la pièce déposée dans A.
    assertThat(manquantes(l)).containsExactlyInAnyOrder("ACTE_NAISSANCE", "PHOTO_IDENTITE");
    assertThat(l.path("dossier").path("enAnalyse").asInt()).isZero();
    // La classe de A ne filtre rien depuis B.
    assertThat(get(DIRECTEUR_B, B, ELEVES + "?classe=" + classe).corps().path("elements"))
        .isEmpty();

    Reponse deA = get(DIRECTEUR_A, A, ELEVES);
    assertThat(deA.corps().path("elements")).hasSize(3);
  }

  @Test
  void lEspaceParentNeRecoitNiStatutNiDossier() throws Exception {
    inscrire(awa, "INSCRIT");
    UUID responsable = creerResponsable(DIRECTEUR_A, A, "Mame", "+221770000001");
    lier(DIRECTEUR_A, A, awa, responsable, "MERE", true);
    relierCompteParent(A, responsable, PARENT_A);

    Reponse enfants = get(PARENT_A, A, "/api/v1/moi/enfants");
    assertThat(enfants.statut()).isEqualTo(200);
    assertThat(enfants.corps()).hasSize(1);
    JsonNode enfant = enfants.corps().get(0);
    assertThat(enfant.has("dossier")).isFalse();
    assertThat(enfant.has("statut")).isFalse();
  }
}
