package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import sn.ecole.inscription.domain.AntiVirus;
import sn.ecole.shared.application.RelaisOutbox;

/**
 * Le vrai ClamAV (même image que infra/docker-compose.yml) : le fichier de test EICAR est détecté,
 * de bout en bout (dépôt, relais de l'outbox, pièce INFECTE et inaccessible). Démarrage du
 * conteneur : une à deux minutes (chargement de la base de signatures).
 */
class AntivirusClamAvTest extends TestInscription {

  /** Fichier de test standard EICAR : inoffensif, reconnu par tous les antivirus. */
  static final String EICAR =
      "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

  static final GenericContainer<?> CLAMAV =
      new GenericContainer<>("clamav/clamav:1.5.4")
          .withExposedPorts(3310)
          // Mémoire : pas de freshclam (base de l'image), pas de double base en mémoire pendant
          // un rechargement. Mêmes réglages que infra/docker-compose.yml.
          .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
          .withEnv("CLAMD_CONF_ConcurrentDatabaseReload", "no")
          .waitingFor(
              Wait.forLogMessage(".*socket found, clamd started.*", 1)
                  .withStartupTimeout(Duration.ofMinutes(5)));

  static {
    CLAMAV.start();
  }

  @DynamicPropertySource
  static void clamav(DynamicPropertyRegistry registre) {
    registre.add("ecole.clamav.hote", CLAMAV::getHost);
    registre.add("ecole.clamav.port", () -> CLAMAV.getMappedPort(3310));
  }

  @Autowired AntiVirus antivirus;
  @Autowired RelaisOutbox relais;

  @BeforeEach
  void vider() {
    superUtilisateur().execute("TRUNCATE ecole.outbox_event, ecole.piece_dossier");
  }

  /**
   * PDF minimal dont un flux contient EICAR : passe le contrôle de type (octets magiques PDF),
   * c'est l'antivirus qui doit l'arrêter.
   */
  static byte[] pdfAvecEicar() {
    String flux = EICAR;
    String pdf =
        "%PDF-1.4\n"
            + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
            + "2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n"
            + "3 0 obj << /Length "
            + flux.length()
            + " >>\nstream\n"
            + flux
            + "\nendstream\nendobj\n"
            + "trailer << /Root 1 0 R >>\n%%EOF\n";
    return pdf.getBytes(StandardCharsets.ISO_8859_1);
  }

  @Test
  void eicarBrutEstDetecteEtUnPdfOrdinaireEstSain() {
    AntiVirus.Resultat infecte = antivirus.analyser(EICAR.getBytes(StandardCharsets.US_ASCII));
    assertThat(infecte.sain()).isFalse();
    assertThat(infecte.signature()).containsIgnoringCase("eicar");

    AntiVirus.Resultat sain =
        antivirus.analyser(
            "%PDF-1.4\n% acte de naissance fictif\n%%EOF".getBytes(StandardCharsets.US_ASCII));
    assertThat(sain.sain()).isTrue();
  }

  @Test
  void eicarDeposeCommePieceEstRefuseDeBoutEnBout() throws Exception {
    UUID eleve = creerEleve(A, "Awa");
    String url = ELEVES + "/" + eleve + "/pieces";

    // EICAR brut : refusé dès le dépôt, ce n'est ni un PDF, ni un JPEG, ni un PNG.
    Reponse brut = deposer(eleve, EICAR.getBytes(StandardCharsets.US_ASCII));
    assertThat(brut.code()).isEqualTo("FICHIER_INVALIDE");

    // EICAR dans un PDF : accepté au dépôt, puis bloqué par ClamAV.
    Reponse r = deposer(eleve, pdfAvecEicar());
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    assertThat(relais.traiterTout()).isEqualTo(1);
    assertThat(get(SECRETARIAT_A, A, url).corps().get(0).path("statut").asText())
        .isEqualTo("INFECTE");
    assertThat(get(SECRETARIAT_A, A, url + "/" + r.id() + "/url").code())
        .isEqualTo("PIECE_NON_DISPONIBLE");
    assertThat(stockage.objets()).isEmpty();
  }

  private Reponse deposer(UUID eleve, byte[] contenu) throws Exception {
    return executer(
        MockMvcRequestBuilders.multipart(HttpMethod.POST, ELEVES + "/" + eleve + "/pieces")
            .file(new MockMultipartFile("fichier", "piece.pdf", "application/pdf", contenu))
            .file(new MockMultipartFile("type", "", "text/plain", "CERTIFICAT".getBytes())),
        SECRETARIAT_A,
        A);
  }
}
