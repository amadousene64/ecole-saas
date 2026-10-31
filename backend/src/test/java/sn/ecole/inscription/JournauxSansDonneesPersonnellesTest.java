package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Aucun nom, téléphone ni donnée de santé dans les journaux applicatifs (CLAUDE.md, données de
 * mineurs). Des valeurs témoins traversent les parcours sensibles, y compris en erreur (conflits,
 * saisies refusées, chiffré altéré qui provoque une erreur interne) ; aucune ne doit apparaître
 * dans un message, un argument ou une trace d'exception, au niveau DEBUG pour le code de
 * l'application.
 */
class JournauxSansDonneesPersonnellesTest extends TestInscription {

  private static final String NOM = "Temoinnom";
  private static final String PRENOM = "Temoinprenom";
  private static final String TELEPHONE = "770009876";
  private static final String SANTE = "Temoinsante asthme";

  private final ListAppender<ILoggingEvent> capture = new ListAppender<>();
  private Logger racine;
  private Level niveauEcole;

  @BeforeEach
  void capturer() {
    racine = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Logger ecole = (Logger) LoggerFactory.getLogger("sn.ecole");
    niveauEcole = ecole.getLevel();
    ecole.setLevel(Level.DEBUG);
    capture.start();
    racine.addAppender(capture);
  }

  @AfterEach
  void arreter() {
    racine.detachAppender(capture);
    ((Logger) LoggerFactory.getLogger("sn.ecole")).setLevel(niveauEcole);
  }

  @Test
  void lesParcoursSensiblesNEcriventAucuneDonneePersonnelleDansLesJournaux() throws Exception {
    // Élève, identité corrigée, doublon d'identifiant national refusé.
    Map<String, Object> identite = identite(PRENOM, NOM);
    identite.put("identifiantNational", "TEMOIN-ID-1");
    UUID eleve = post(SECRETARIAT_A, A, ELEVES, identite).id();
    assertThat(post(SECRETARIAT_A, A, ELEVES, identite).statut()).isEqualTo(409);
    Map<String, Object> invalide = identite(PRENOM + "9", NOM);
    assertThat(post(SECRETARIAT_A, A, ELEVES, invalide).statut()).isEqualTo(400);

    // Données sensibles : écriture, lecture, puis chiffré altéré (erreur interne journalisée).
    String url = ELEVES + "/" + eleve + "/donnees-sensibles";
    put(SECRETARIAT_A, A, url, 0, corps("sante", SANTE));
    assertThat(get(DIRECTEUR_A, A, url).corps().path("sante").asText()).isEqualTo(SANTE);
    superUtilisateur()
        .update(
            "UPDATE ecole.eleve_dossier_sensible SET sante = set_byte(sante, 20, get_byte(sante, 20) # 1)"
                + " WHERE eleve_id = ?",
            eleve);
    assertThat(get(DIRECTEUR_A, A, url).statut()).isEqualTo(500);

    // Responsable : téléphone invalide refusé, puis lien et code d'activation.
    Object faux = corps("prenoms", PRENOM, "nom", NOM, "telephone", "12" + TELEPHONE + "999999");
    assertThat(post(SECRETARIAT_A, A, "/api/v1/responsables", faux).statut()).isEqualTo(400);
    UUID responsable = creerResponsable(SECRETARIAT_A, A, PRENOM, TELEPHONE);
    lier(SECRETARIAT_A, A, eleve, responsable, "MERE", true);
    String code =
        post(SECRETARIAT_A, A, "/api/v1/responsables/" + responsable + "/codes-activation", null)
            .corps()
            .path("code")
            .asText();
    post(
        UUID.randomUUID(),
        A,
        "/api/v1/activations",
        corps("code", code.substring(0, 5) + "ZZZZZZZZ"));
    post(UUID.randomUUID(), A, "/api/v1/activations", corps("code", code));

    // Import d'une liste : ligne en erreur (date invalide), puis import refusé.
    byte[] liste =
        ("Prénoms;Nom;Sexe;Date de naissance;Lieu de naissance;Responsable prénoms;"
                + "Responsable nom;Responsable téléphone\n"
                + PRENOM
                + ";"
                + NOM
                + ";F;pas une date;Ville Fictive;"
                + PRENOM
                + ";"
                + NOM
                + ";"
                + TELEPHONE
                + "\n")
            .getBytes(StandardCharsets.UTF_8);
    for (String chemin : List.of("/api/v1/imports-eleves/validation", "/api/v1/imports-eleves")) {
      var requete =
          MockMvcRequestBuilders.multipart(HttpMethod.POST, chemin)
              .file(new MockMultipartFile("fichier", "eleves.csv", "text/csv", liste))
              .param("empreinte", "0".repeat(64));
      assertThat(executer(requete, SECRETARIAT_A, A).statut()).isBetween(200, 499);
    }

    List<String> journaux =
        capture.list.stream()
            .map(
                e -> {
                  IThrowableProxy t = e.getThrowableProxy();
                  return e.getFormattedMessage()
                      + " "
                      + (t == null ? "" : ThrowableProxyUtil.asString(t));
                })
            .toList();
    assertThat(journaux).isNotEmpty();
    String secret = code.substring(5).replace("-", "");
    for (String temoin :
        List.of(
            NOM,
            NOM.toUpperCase(),
            PRENOM,
            TELEPHONE,
            "+221" + TELEPHONE,
            "Temoinsante",
            "TEMOIN-ID-1",
            secret)) {
      assertThat(journaux)
          .as("journaux contenant « %s »", temoin)
          .noneMatch(l -> l.contains(temoin));
    }
  }
}
