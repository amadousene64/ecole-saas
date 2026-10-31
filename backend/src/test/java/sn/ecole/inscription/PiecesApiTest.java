package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.COMPTABLE_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.PARENT_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;
import static sn.ecole.Fixtures.SURVEILLANT_A;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import sn.ecole.ImagesFictives;
import sn.ecole.inscription.application.DossierSensibleService;
import sn.ecole.inscription.domain.AntiVirus;
import sn.ecole.inscription.domain.ControlePiece;
import sn.ecole.shared.application.RelaisOutbox;

/**
 * Pièces du dossier : contrôle du type, cycle EN_ANALYSE → SAIN / INFECTE par le relais, URL
 * refusée tant que la pièce n'est pas saine. L'antivirus est ici simulé ; le vrai ClamAV (EICAR)
 * est couvert par AntivirusClamAvTest.
 */
@Import(PiecesApiTest.AntivirusSimule.class)
class PiecesApiTest extends TestInscription {

  /** « Infecté » si le contenu contient VIRUS-FICTIF ; panne simulée sur demande. */
  @TestConfiguration(proxyBeanMethods = false)
  static class AntivirusSimule {

    static final AtomicBoolean EN_PANNE = new AtomicBoolean();

    @Bean
    @Primary
    AntiVirus antivirusSimule() {
      return contenu -> {
        if (EN_PANNE.get()) {
          throw new IllegalStateException("clamd injoignable");
        }
        boolean infecte = new String(contenu, StandardCharsets.ISO_8859_1).contains("VIRUS-FICTIF");
        return new AntiVirus.Resultat(!infecte, infecte ? "Fictif.Test" : null);
      };
    }
  }

  @Autowired RelaisOutbox relais;

  private UUID eleve;
  private String url;

  @BeforeEach
  void eleve() throws Exception {
    AntivirusSimule.EN_PANNE.set(false);
    superUtilisateur().execute("TRUNCATE ecole.outbox_event, ecole.piece_dossier");
    eleve = creerEleve(A, "Awa");
    url = ELEVES + "/" + eleve + "/pieces";
  }

  private Reponse deposer(UUID qui, UUID ecole, String type, byte[] contenu) throws Exception {
    return executer(
        MockMvcRequestBuilders.multipart(HttpMethod.POST, url)
            .file(new MockMultipartFile("fichier", "acte.pdf", "application/pdf", contenu))
            .file(new MockMultipartFile("type", "", "text/plain", type.getBytes())),
        qui,
        ecole);
  }

  private static byte[] pdf(String texte) {
    return ("%PDF-1.4\n% document fictif\n" + texte + "\n%%EOF")
        .getBytes(StandardCharsets.US_ASCII);
  }

  @Test
  void unePieceSaineNEstDisponibleQuApresAnalyse() throws Exception {
    Reponse r = deposer(SECRETARIAT_A, A, "ACTE_NAISSANCE", pdf("acte fictif"));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    assertThat(r.corps().path("statut").asText()).isEqualTo("EN_ANALYSE");
    assertThat(r.corps().path("contentType").asText()).isEqualTo("application/pdf");
    assertThat(stockage.objets()).hasSize(1);

    Reponse attente = get(SECRETARIAT_A, A, url + "/" + r.id() + "/url");
    assertThat(attente.statut()).isEqualTo(409);
    assertThat(attente.code()).isEqualTo("PIECE_NON_DISPONIBLE");

    assertThat(relais.traiterTout()).isEqualTo(1);
    assertThat(get(DIRECTEUR_A, A, url).corps().get(0).path("statut").asText()).isEqualTo("SAIN");
    Reponse lien = get(SECRETARIAT_A, A, url + "/" + r.id() + "/url");
    assertThat(lien.statut()).isEqualTo(200);
    assertThat(lien.corps().path("url").asText())
        .contains("/eleves/" + eleve + "/pieces/")
        .contains("expire=300")
        .contains("disposition=attachment");
    assertThat(journal("PIECE_ANALYSEE"))
        .singleElement()
        .satisfies(j -> assertThat(j.get("acteur_id")).isEqualTo(new UUID(0, 0)));
  }

  @Test
  void unePieceInfecteeEstRetireeEtResteInaccessible() throws Exception {
    Reponse r = deposer(SECRETARIAT_A, A, "CERTIFICAT_SCOLARITE", pdf("VIRUS-FICTIF"));
    relais.traiterTout();
    Reponse lue = get(SECRETARIAT_A, A, url);
    assertThat(lue.corps().get(0).path("statut").asText()).isEqualTo("INFECTE");
    assertThat(stockage.objets()).isEmpty();
    Reponse refus = get(DIRECTEUR_A, A, url + "/" + r.id() + "/url");
    assertThat(refus.code()).isEqualTo("PIECE_NON_DISPONIBLE");
    assertThat(refus.corps().path("detail").asText()).contains("antivirus");
    assertThat((String) journal("PIECE_INFECTEE").get(0).get("details")).contains("Fictif.Test");
  }

  @Test
  void antivirusEnPanneLaPieceResteEnAnalyseEtSeraRetentee() throws Exception {
    Reponse r = deposer(SECRETARIAT_A, A, "PHOTO_IDENTITE", pdf("photo fictive"));
    AntivirusSimule.EN_PANNE.set(true);
    assertThat(relais.traiterTout()).isZero();
    assertThat(get(SECRETARIAT_A, A, url).corps().get(0).path("statut").asText())
        .isEqualTo("EN_ANALYSE");
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT tentatives FROM ecole.outbox_event WHERE id_agregat = ?",
                    Integer.class,
                    r.id()))
        .isEqualTo(1);
    assertThat(get(SECRETARIAT_A, A, url + "/" + r.id() + "/url").code())
        .isEqualTo("PIECE_NON_DISPONIBLE");
  }

  @Test
  void leTypeVientDuContenuPasDuNomNiDeLEnTete() throws Exception {
    Reponse exe =
        deposer(
            SECRETARIAT_A,
            A,
            "CERTIFICAT_SCOLARITE",
            "MZ\u0090\u0000 programme".getBytes(StandardCharsets.ISO_8859_1));
    assertThat(exe.code()).isEqualTo("FICHIER_INVALIDE");
    Reponse html =
        deposer(
            SECRETARIAT_A,
            A,
            "CERTIFICAT_SCOLARITE",
            "<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII));
    assertThat(html.code()).isEqualTo("FICHIER_INVALIDE");
    assertThat(deposer(SECRETARIAT_A, A, "AUTRE", pdf("x")).code()).isEqualTo("CHAMP_INVALIDE");
    assertThat(stockage.objets()).isEmpty();
  }

  @Test
  void seulesLaDirectionEtLeSecretariatAccedentAuxPiecesOrdinaires() throws Exception {
    Reponse r = deposer(DIRECTEUR_A, A, "ACTE_NAISSANCE", pdf("acte"));
    relais.traiterTout();
    assertThat(deposer(CENSEUR_A, A, "ACTE_NAISSANCE", pdf("acte")).statut()).isEqualTo(403);
    // Le censeur ne voit que les certificats médicaux : ici, aucun.
    assertThat(get(CENSEUR_A, A, url).corps()).isEmpty();
    assertThat(get(CENSEUR_A, A, url + "/" + r.id() + "/url").statut()).isEqualTo(403);
    assertThat(delete(CENSEUR_A, A, url + "/" + r.id()).statut()).isEqualTo(403);
    for (UUID autre : List.of(SURVEILLANT_A, COMPTABLE_A, ENSEIGNANT_A_PARENT_B)) {
      assertThat(get(autre, A, url).statut()).isEqualTo(403);
      assertThat(get(autre, A, url + "/" + r.id() + "/url").statut()).isEqualTo(403);
    }
  }

  @Test
  void leCertificatMedicalSuitLesReglesDesDonneesSensibles() throws Exception {
    // Le secrétariat le dépose...
    Reponse medical = deposer(SECRETARIAT_A, A, "CERTIFICAT_MEDICAL", pdf("certificat fictif"));
    assertThat(medical.statut()).as(medical.corps().toString()).isEqualTo(201);
    Reponse scolarite = deposer(SECRETARIAT_A, A, "CERTIFICAT_SCOLARITE", pdf("scolarite"));
    relais.traiterTout();
    String lienMedical = url + "/" + medical.id() + "/url";

    // ... le voit dans la liste, mais ne peut pas l'ouvrir.
    assertThat(get(SECRETARIAT_A, A, url).corps()).hasSize(2);
    Reponse refus = get(SECRETARIAT_A, A, lienMedical);
    assertThat(refus.statut()).isEqualTo(403);
    assertThat(refus.code()).isEqualTo("ACCES_REFUSE");
    for (UUID autre : List.of(SURVEILLANT_A, COMPTABLE_A, ENSEIGNANT_A_PARENT_B, PARENT_A)) {
      assertThat(get(autre, A, lienMedical).statut()).isEqualTo(403);
    }
    assertThat(get(DIRECTEUR_B, B, lienMedical).statut()).isEqualTo(404);
    assertThat(journalDeLEleve(DossierSensibleService.LECTURE)).isEmpty();

    // Le censeur ne voit que lui, et l'ouvre ; le directeur aussi.
    Reponse vueCenseur = get(CENSEUR_A, A, url);
    assertThat(vueCenseur.corps()).hasSize(1);
    assertThat(vueCenseur.corps().get(0).path("type").asText()).isEqualTo("CERTIFICAT_MEDICAL");
    assertThat(get(CENSEUR_A, A, url + "/" + scolarite.id() + "/url").statut()).isEqualTo(403);
    assertThat(get(CENSEUR_A, A, lienMedical).statut()).isEqualTo(200);
    assertThat(get(DIRECTEUR_A, A, lienMedical).statut()).isEqualTo(200);

    // Chaque ouverture est journalisée comme une lecture de données sensibles, avec son auteur.
    List<Map<String, Object>> lectures = journalDeLEleve(DossierSensibleService.LECTURE);
    assertThat(lectures)
        .extracting(j -> j.get("acteur_id"))
        .containsExactly(CENSEUR_A, DIRECTEUR_A);
    assertThat(lectures)
        .allSatisfy(
            j ->
                assertThat((String) j.get("details"))
                    .contains("certificat_medical")
                    .contains(medical.id().toString()));

    // Le secrétariat peut le retirer sans l'avoir lu.
    assertThat(delete(SECRETARIAT_A, A, url + "/" + medical.id()).statut()).isEqualTo(204);
  }

  @Test
  void uneImageDeposeeEstReencodeeSansExifNiGps() throws Exception {
    byte[] photo = ImagesFictives.jpegAvecGps(800, 600);
    assertThat(ImagesFictives.contient(photo, "Exif")).isTrue();
    assertThat(ImagesFictives.segmentsApp1(photo)).isEqualTo(1);

    Reponse r = deposer(SECRETARIAT_A, A, "PHOTO_IDENTITE", photo);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    assertThat(r.corps().path("contentType").asText()).isEqualTo("image/jpeg");
    byte[] stocke = stockage.objets().values().iterator().next().contenu();
    assertThat(stocke).isNotEqualTo(photo);
    assertThat(r.corps().path("taille").asInt()).isEqualTo(stocke.length);
    assertThat(ImagesFictives.segmentsApp1(stocke)).isZero();
    assertThat(ImagesFictives.contient(stocke, "Exif")).isFalse();
    assertThat(ImagesFictives.contient(stocke, "GPS")).isFalse();
    BufferedImage relue = ImageIO.read(new ByteArrayInputStream(stocke));
    assertThat(relue.getWidth()).isEqualTo(800);
    assertThat(relue.getHeight()).isEqualTo(600);
  }

  @Test
  void unPngDeposeEstReencodeEtUneGrandePhotoEstReduite() throws Exception {
    byte[] png = ImagesFictives.pngAvecTexte("Commentaire fictif GPS 14.69 -17.44");
    Reponse r = deposer(SECRETARIAT_A, A, "ACTE_NAISSANCE", png);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    byte[] stocke = stockage.objets().values().iterator().next().contenu();
    assertThat(ImagesFictives.contient(stocke, "Commentaire fictif")).isFalse();

    stockage.vider();
    Reponse grande =
        deposer(SECRETARIAT_A, A, "ACTE_NAISSANCE", ImagesFictives.jpegAvecGps(4000, 3000));
    assertThat(grande.statut()).as(grande.corps().toString()).isEqualTo(201);
    BufferedImage relue =
        ImageIO.read(
            new ByteArrayInputStream(stockage.objets().values().iterator().next().contenu()));
    assertThat(Math.max(relue.getWidth(), relue.getHeight()))
        .isLessThanOrEqualTo(ControlePiece.COTE_IMAGE_MAX);
  }

  private List<Map<String, Object>> journalDeLEleve(String action) {
    return journal(action).stream().filter(j -> eleve.equals(j.get("id_cible"))).toList();
  }

  @Test
  void lEcoleBNAccedePasAuxPiecesDeLEcoleA() throws Exception {
    Reponse r = deposer(SECRETARIAT_A, A, "ACTE_NAISSANCE", pdf("acte"));
    relais.traiterTout();
    assertThat(get(DIRECTEUR_B, B, url).code()).isEqualTo("ELEVE_INTROUVABLE");
    assertThat(get(DIRECTEUR_B, B, url + "/" + r.id() + "/url").statut()).isEqualTo(404);
    UUID eleveB = creerEleve(B, "Dior");
    assertThat(get(DIRECTEUR_B, B, ELEVES + "/" + eleveB + "/pieces/" + r.id() + "/url").code())
        .isEqualTo("PIECE_INTROUVABLE");
  }

  @Test
  void laSuppressionRetireLeFichierEtLElevePorteurNeSeSupprimePas() throws Exception {
    Reponse r = deposer(SECRETARIAT_A, A, "ACTE_NAISSANCE", pdf("acte"));
    assertThat(delete(DIRECTEUR_A, A, ELEVES + "/" + eleve).code()).isEqualTo("ELEVE_UTILISE");
    assertThat(delete(SECRETARIAT_A, A, url + "/" + r.id()).statut()).isEqualTo(204);
    assertThat(stockage.objets()).isEmpty();
    assertThat(get(SECRETARIAT_A, A, url).corps()).isEmpty();
    // L'analyse arrivant après la suppression ne fait rien.
    assertThat(relais.traiterTout()).isEqualTo(1);
  }
}
