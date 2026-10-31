package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/** Import d'une liste d'élèves : validation à blanc, import tout ou rien, garde-fous. */
class ImportElevesApiTest extends TestInscription {

  private static final String IMPORTS = "/api/v1/imports-eleves";
  private static final String ENTETES =
      "Matricule;Prénoms;Nom;Sexe;Date de naissance;Lieu de naissance;Classe;"
          + "Responsable prénoms;Responsable nom;Responsable téléphone;Responsable lien\n";

  private UUID annee;

  @BeforeEach
  void structure() throws Exception {
    annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    Reponse r =
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
                2));
    assertThat(r.statut()).isEqualTo(201);
  }

  private Reponse envoyer(UUID qui, UUID ecole, String url, byte[] fichier, String... params)
      throws Exception {
    var requete =
        MockMvcRequestBuilders.multipart(HttpMethod.POST, url)
            .file(new MockMultipartFile("fichier", "eleves.csv", "text/csv", fichier));
    for (int i = 0; i < params.length; i += 2) {
      requete.param(params[i], params[i + 1]);
    }
    return executer(requete, qui, ecole);
  }

  private Reponse valider(byte[] fichier) throws Exception {
    return envoyer(SECRETARIAT_A, A, IMPORTS + "/validation", fichier, "annee", annee.toString());
  }

  private Reponse importer(byte[] fichier, String empreinte) throws Exception {
    return envoyer(
        SECRETARIAT_A, A, IMPORTS, fichier, "empreinte", empreinte, "annee", annee.toString());
  }

  private static byte[] csv(String... lignes) {
    return (ENTETES + String.join("\n", lignes) + "\n").getBytes(StandardCharsets.UTF_8);
  }

  private int nombre(String table) {
    return superUtilisateur().queryForObject("SELECT count(*) FROM ecole." + table, Integer.class);
  }

  private static JsonNode ligne(Reponse r, int numero) {
    for (JsonNode l : r.corps().path("details")) {
      if (l.path("numero").asInt() == numero) {
        return l;
      }
    }
    throw new AssertionError("Ligne " + numero + " absente du rapport");
  }

  @Test
  void laValidationRapporteLigneParLigneSansRienEcrire() throws Exception {
    byte[] fichier =
        csv(
            ";Awa;Fictif;F;14/05/2014;Ville Fictive;6E A;Mère;Fictive;770000001;mère",
            ";Binta;Fictif;X;14/05/2014;Ville Fictive;;;;;",
            ";Coumba;Fictif;F;31/02/2014;Ville Fictive;;;;;",
            ";Dior;Fictif;F;2014-06-01;Ville Fictive;6E Z;;;;");
    Reponse r = valider(fichier);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    assertThat(r.corps().path("lignes").asInt()).isEqualTo(4);
    assertThat(r.corps().path("erreurs").asInt()).isEqualTo(3);
    assertThat(r.corps().path("importable").asBoolean()).isFalse();
    assertThat(r.corps().path("empreinte").asText()).hasSize(64);
    assertThat(ligne(r, 2).path("statut").asText()).isEqualTo("AVERTISSEMENT"); // SN par défaut
    assertThat(ligne(r, 3).path("messages").toString()).contains("sexe");
    assertThat(ligne(r, 4).path("messages").toString()).contains("date_naissance");
    assertThat(ligne(r, 5).path("messages").toString()).contains("classe inconnue");
    // Les messages ne recopient jamais les valeurs saisies.
    assertThat(r.corps().toString()).doesNotContain("Binta").doesNotContain("Fictive");
    assertThat(nombre("eleve")).isZero();
    assertThat(nombre("responsable")).isZero();
  }

  @Test
  void lImportCreeElevesResponsablesLiensEtInscriptions() throws Exception {
    byte[] fichier =
        csv(
            "REPRISE-001;Awa;Fictif;F;14/05/2014;Ville Fictive;6E A;Mère;Fictive;77 000 00 01;MERE",
            ";Binta;Fictif;F;02/03/2013;Ville Fictive;6E A;Mère;Fictive;770000001;MERE",
            ";Coumba;Fictif;F;02/03/2015;Ville Fictive;;;;;");
    Reponse v = valider(fichier);
    assertThat(v.corps().path("importable").asBoolean()).isTrue();

    Reponse r = importer(fichier, v.corps().path("empreinte").asText());
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    assertThat(r.corps().path("elevesCrees").asInt()).isEqualTo(3);
    assertThat(r.corps().path("inscriptions").asInt()).isEqualTo(2);
    assertThat(r.corps().path("responsablesCrees").asInt()).isEqualTo(1);
    assertThat(nombre("eleve")).isEqualTo(3);
    assertThat(nombre("responsable")).isEqualTo(1); // un seul responsable pour les deux sœurs
    assertThat(nombre("lien_parental")).isEqualTo(2);
    assertThat(nombre("inscription")).isEqualTo(2);
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT count(*) FROM ecole.eleve WHERE matricule = 'REPRISE-001'",
                    Integer.class))
        .isEqualTo(1);
    assertThat(journal("IMPORT_ELEVES")).hasSize(1);
  }

  @Test
  void unFichierNeSImporteQuUneFoisEtSeulementTelQuValide() throws Exception {
    byte[] fichier = csv(";Awa;Fictif;F;14/05/2014;Ville Fictive;;;;;");
    String empreinte = valider(fichier).corps().path("empreinte").asText();
    byte[] modifie = csv(";Awa;Fictif;F;15/05/2014;Ville Fictive;;;;;");
    assertThat(importer(modifie, empreinte).code()).isEqualTo("EMPREINTE_DIFFERENTE");
    assertThat(importer(fichier, empreinte).statut()).isEqualTo(201);
    assertThat(importer(fichier, empreinte).code()).isEqualTo("IMPORT_DEJA_FAIT");
    assertThat(nombre("eleve")).isEqualTo(1);
  }

  @Test
  void unFichierAvecErreursNEcritRienEtRenvoieLeRapport() throws Exception {
    byte[] fichier =
        csv(
            ";Awa;Fictif;F;14/05/2014;Ville Fictive;;;;;",
            ";Binta;Fictif;F;pas une date;Ville Fictive;;;;;");
    String empreinte = valider(fichier).corps().path("empreinte").asText();
    Reponse r = importer(fichier, empreinte);
    assertThat(r.statut()).isEqualTo(422);
    assertThat(r.corps().path("erreurs").asInt()).isEqualTo(1);
    assertThat(nombre("eleve")).isZero();
  }

  @Test
  void lesDoublonsEtMatriculesPrisSontSignales() throws Exception {
    creerEleve(A, "Awa");
    String matriculeExistant =
        superUtilisateur().queryForObject("SELECT matricule FROM ecole.eleve", String.class);
    byte[] fichier =
        csv(
            matriculeExistant + ";Khady;Fictif;F;14/05/2014;Ville Fictive;;;;;",
            "X-1;Mariama;Fictif;F;14/05/2014;Ville Fictive;;;;;",
            "X-1;Ndeye;Fictif;F;14/05/2014;Ville Fictive;;;;;",
            ";Awa;Fictif;F;" + "14/05/2012" + ";Ville Fictive;;;;;");
    Reponse r = valider(fichier);
    assertThat(ligne(r, 2).path("messages").toString()).contains("déjà attribué");
    assertThat(ligne(r, 4).path("messages").toString()).contains("en double");
    assertThat(ligne(r, 5).path("statut").asText()).isEqualTo("AVERTISSEMENT");
    assertThat(ligne(r, 5).path("messages").toString()).contains("doublon");
  }

  @Test
  void laCapaciteEstControleeSelonLaPolitiqueDeLEcole() throws Exception {
    byte[] fichier =
        csv(
            ";Awa;Fictif;F;14/05/2014;Ville Fictive;6E A;;;;",
            ";Binta;Fictif;F;14/05/2013;Ville Fictive;6E A;;;;",
            ";Coumba;Fictif;F;14/05/2012;Ville Fictive;6E A;;;;");
    assertThat(ligne(valider(fichier), 4).path("statut").asText()).isEqualTo("AVERTISSEMENT");
    Reponse p = get(DIRECTEUR_A, A, "/api/v1/parametres-inscription");
    put(
        DIRECTEUR_A,
        A,
        "/api/v1/parametres-inscription",
        versionEtag(p),
        corps("formatMatricule", "{AAAA}{SEQ:5}", "politiqueCapacite", "BLOQUER"));
    assertThat(ligne(valider(fichier), 4).path("statut").asText()).isEqualTo("ERREUR");
  }

  @Test
  void lesColonnesObligatoiresSontExigees() throws Exception {
    byte[] fichier = "Prénoms;Nom\nAwa;Fictif\n".getBytes(StandardCharsets.UTF_8);
    Reponse r = valider(fichier);
    assertThat(r.statut()).isEqualTo(400);
    assertThat(r.code()).isEqualTo("COLONNES_MANQUANTES");
  }

  @Test
  void unCsvWindows1252AvecVirgulesEstLu() throws Exception {
    byte[] fichier =
        ("Prénoms,Nom,Sexe,Date de naissance,Lieu de naissance\n"
                + "Élodie,Fictif,F,14/05/2014,Thiès\n")
            .getBytes(Charset.forName("windows-1252"));
    Reponse r = valider(fichier);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    assertThat(r.corps().path("erreurs").asInt()).isZero();
  }

  @Test
  void unXlsxAvecDatesExcelEtFormuleEstLuSansEvaluerLaFormule() throws Exception {
    ByteArrayOutputStream sortie = new ByteArrayOutputStream();
    try (XSSFWorkbook classeur = new XSSFWorkbook()) {
      Sheet feuille = classeur.createSheet("Élèves");
      Row entetes = feuille.createRow(0);
      String[] noms = {
        "Prénoms", "Nom", "Sexe", "Date de naissance", "Lieu de naissance", "Classe"
      };
      for (int i = 0; i < noms.length; i++) {
        entetes.createCell(i).setCellValue(noms[i]);
      }
      CellStyle date = classeur.createCellStyle();
      date.setDataFormat(classeur.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
      Row eleve = feuille.createRow(1);
      eleve.createCell(0).setCellValue("Awa");
      eleve.createCell(1).setCellValue("Fictif");
      eleve.createCell(2).setCellValue("F");
      var cellule = eleve.createCell(3);
      cellule.setCellValue(LocalDate.of(2014, 5, 14));
      cellule.setCellStyle(date);
      eleve.createCell(4).setCellValue("Ville Fictive");
      // Formule jamais évaluée : seule la valeur en cache compte (« ZZ », classe inconnue).
      var formule = eleve.createCell(5);
      formule.setCellFormula("HYPERLINK(\"http://exemple.test\",\"6E A\")");
      formule.setCellValue("ZZ");
      classeur.write(sortie);
    }
    Reponse r = valider(sortie.toByteArray());
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    assertThat(r.corps().path("lignes").asInt()).isEqualTo(1);
    // La date Excel est lue (aucune erreur de date) ; la valeur en cache « ZZ » est lue, pas « 6E A
    // ».
    String messages = ligne(r, 2).path("messages").toString();
    assertThat(messages).doesNotContain("date_naissance").contains("classe inconnue");
  }

  @Test
  void unXlsxDePlusDe2000LignesEstRefuse() throws Exception {
    ByteArrayOutputStream sortie = new ByteArrayOutputStream();
    try (XSSFWorkbook classeur = new XSSFWorkbook()) {
      Sheet feuille = classeur.createSheet("Élèves");
      feuille.createRow(0).createCell(0).setCellValue("Prénoms");
      for (int i = 1; i <= 2_001; i++) {
        feuille.createRow(i).createCell(0).setCellValue("Fictif");
      }
      classeur.write(sortie);
    }
    assertThat(valider(sortie.toByteArray()).code()).isEqualTo("IMPORT_TROP_GRAND");
  }

  @Test
  void unFichierQuiNEstNiCsvNiXlsxEstRefuse() throws Exception {
    byte[] faux = {'P', 'K', 3, 4, 0, 0, 0, 0, 1, 2, 3};
    assertThat(valider(faux).code()).isEqualTo("FICHIER_INVALIDE");
    assertThat(valider(new byte[] {0, 1, 2, 0}).code()).isEqualTo("FICHIER_INVALIDE");
    assertThat(valider(new byte[2_000_001]).code()).isEqualTo("FICHIER_INVALIDE");
  }

  @Test
  void seulsLaDirectionEtLeSecretariatImportent() throws Exception {
    byte[] fichier = csv(";Awa;Fictif;F;14/05/2014;Ville Fictive;;;;;");
    assertThat(envoyer(CENSEUR_A, A, IMPORTS + "/validation", fichier).statut()).isEqualTo(403);
    assertThat(envoyer(CENSEUR_A, A, IMPORTS, fichier, "empreinte", "x").statut()).isEqualTo(403);
    assertThat(envoyer(DIRECTEUR_A, A, IMPORTS + "/validation", fichier).statut()).isEqualTo(200);
  }

  @Test
  void lEcoleBNePeutPasImporterDansUneAnneeDeLEcoleA() throws Exception {
    byte[] fichier = csv(";Awa;Fictif;F;14/05/2014;Ville Fictive;6E A;;;;");
    Reponse r =
        envoyer(DIRECTEUR_B, B, IMPORTS + "/validation", fichier, "annee", annee.toString());
    assertThat(r.code()).isEqualTo("ANNEE_INTROUVABLE");
  }
}
