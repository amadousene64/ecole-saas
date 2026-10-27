package sn.ecole.inscription;

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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.ecole.shared.application.ChiffrementDonnees;
import sn.ecole.shared.application.ExecutionPourTenant;

/** Santé, allergies, situation familiale : chiffrement effectif, droits, journal des lectures. */
class DonneesSensiblesApiTest extends TestInscription {

  private static final String SANTE = "Asthme léger, inhalateur en cas de crise (fictif)";
  private static final String ALLERGIES = "Arachides (fictif)";

  @Autowired ChiffrementDonnees chiffrement;
  @Autowired ExecutionPourTenant execution;

  private UUID eleve;
  private String url;

  @BeforeEach
  void eleve() throws Exception {
    eleve = creerEleve(A, "Awa");
    url = ELEVES + "/" + eleve + "/donnees-sensibles";
  }

  private Reponse ecrire(UUID qui, int version, Object corps) throws Exception {
    return put(qui, A, url, version, corps);
  }

  @Test
  void leSecretariatEcritSansPouvoirRelire() throws Exception {
    Reponse etat = get(SECRETARIAT_A, A, url + "/etat");
    assertThat(etat.statut()).isEqualTo(200);
    assertThat(etat.corps().path("santeRenseignee").asBoolean()).isFalse();

    Reponse r =
        ecrire(SECRETARIAT_A, versionEtag(etat), corps("sante", SANTE, "allergies", ALLERGIES));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    assertThat(r.corps().path("santeRenseignee").asBoolean()).isTrue();
    assertThat(r.corps().path("allergiesRenseignees").asBoolean()).isTrue();
    assertThat(r.corps().path("situationFamilialeRenseignee").asBoolean()).isFalse();
    // La réponse ne contient jamais le contenu.
    assertThat(r.brut().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Asthme");

    assertThat(get(SECRETARIAT_A, A, url).statut()).isEqualTo(403);
  }

  @Test
  void laDirectionEtLeCenseurLisentEtChaqueLectureEstJournalisee() throws Exception {
    ecrire(DIRECTEUR_A, 0, corps("sante", SANTE, "situationFamiliale", "Garde alternée (fictif)"));

    Reponse lu = get(CENSEUR_A, A, url);
    assertThat(lu.statut()).isEqualTo(200);
    assertThat(lu.corps().path("sante").asText()).isEqualTo(SANTE);
    assertThat(lu.corps().path("allergies").isNull()).isTrue();
    assertThat(lu.brut().getHeader("Cache-Control")).isEqualTo("no-store");
    get(DIRECTEUR_A, A, url);

    var lectures = journal("DONNEES_SENSIBLES_LUES");
    assertThat(lectures).hasSize(2);
    assertThat(lectures.get(0))
        .containsEntry("tenant_id", A)
        .containsEntry("acteur_id", CENSEUR_A)
        .containsEntry("id_cible", eleve);
    assertThat((String) lectures.get(0).get("details"))
        .contains("SANTE", "SITUATION_FAMILIALE")
        .doesNotContain("Asthme")
        .doesNotContain("Garde");
    assertThat((String) journal("DONNEES_SENSIBLES_MODIFIEES").get(0).get("details"))
        .doesNotContain("Asthme");
  }

  @Test
  void lesAutresRolesNeLisentRienEtUneLectureRefuseeNEstPasJournalisee() throws Exception {
    ecrire(DIRECTEUR_A, 0, corps("sante", SANTE));
    relierCompteEleve(eleve, ELEVE_A);
    for (UUID qui :
        new UUID[] {SURVEILLANT_A, COMPTABLE_A, ENSEIGNANT_A_PARENT_B, PARENT_A, ELEVE_A}) {
      assertThat(get(qui, A, url).statut()).as("lecture par %s", qui).isEqualTo(403);
      assertThat(ecrire(qui, 1, corps("sante", "x")).statut()).isEqualTo(403);
    }
    assertThat(get(SURVEILLANT_A, A, url + "/etat").statut()).isEqualTo(403);
    assertThat(journal("DONNEES_SENSIBLES_LUES")).isEmpty();
  }

  @Test
  void lesDonneesSontEffectivementChiffreesEnBase() throws Exception {
    ecrire(DIRECTEUR_A, 0, corps("sante", SANTE, "allergies", ALLERGIES));

    Map<String, Object> ligne =
        superUtilisateur()
            .queryForMap(
                "SELECT sante, allergies, situation_familiale FROM ecole.eleve_dossier_sensible"
                    + " WHERE eleve_id = ?",
                eleve);
    byte[] sante = (byte[]) ligne.get("sante");
    assertThat(new String(sante, StandardCharsets.ISO_8859_1)).doesNotContain("Asthme");
    assertThat(new String(sante, StandardCharsets.UTF_8)).doesNotContain("Asthme");
    assertThat(sante[0]).isEqualTo((byte) 1); // format v1
    assertThat(ligne.get("situation_familiale")).isNull();
    // Déchiffrable avec la clé de l'application, dans le contexte de cet élève et de ce champ.
    String clair =
        execution.lire(A, () -> chiffrement.dechiffrer(sante, A + "|eleve|" + eleve + "|SANTE"));
    assertThat(clair).isEqualTo(SANTE);
  }

  @Test
  void unChiffreRecopieVersUnAutreEleveNeSeDechiffrePas() throws Exception {
    ecrire(DIRECTEUR_A, 0, corps("sante", SANTE));
    UUID autre = creerEleve(A, "Binta");
    String urlAutre = ELEVES + "/" + autre + "/donnees-sensibles";
    put(DIRECTEUR_A, A, urlAutre, 0, corps("sante", "Rien à signaler (fictif)"));
    superUtilisateur()
        .update(
            "UPDATE ecole.eleve_dossier_sensible SET sante = (SELECT sante FROM"
                + " ecole.eleve_dossier_sensible WHERE eleve_id = ?) WHERE eleve_id = ?",
            eleve,
            autre);
    Reponse r = get(DIRECTEUR_A, A, urlAutre);
    assertThat(r.statut()).isEqualTo(500);
    assertThat(r.brut().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Asthme");
  }

  @Test
  void laModificationPartielleNeTouchePasAuxAutresChamps() throws Exception {
    Reponse r = ecrire(SECRETARIAT_A, 0, corps("sante", SANTE, "allergies", ALLERGIES));
    r = ecrire(SECRETARIAT_A, versionEtag(r), corps("allergies", ""));
    assertThat(r.corps().path("santeRenseignee").asBoolean()).isTrue();
    assertThat(r.corps().path("allergiesRenseignees").asBoolean()).isFalse();
    assertThat(ecrire(SECRETARIAT_A, 0, corps("sante", "x")).code()).isEqualTo("CONFLIT_VERSION");
    assertThat(get(DIRECTEUR_A, A, url).corps().path("sante").asText()).isEqualTo(SANTE);
  }

  @Test
  void uneValeurTropLongueEstRefusee() throws Exception {
    assertThat(ecrire(DIRECTEUR_A, 0, corps("allergies", "x".repeat(1001))).statut())
        .isEqualTo(400);
  }

  @Test
  void lEcoleBNAccedePasAuxDonneesDeLEcoleA() throws Exception {
    ecrire(DIRECTEUR_A, 0, corps("sante", SANTE));
    assertThat(get(DIRECTEUR_B, B, url).code()).isEqualTo("ELEVE_INTROUVABLE");
    assertThat(put(DIRECTEUR_B, B, url, 1, corps("sante", "x")).statut()).isEqualTo(404);
    assertThat(journal("DONNEES_SENSIBLES_LUES")).isEmpty();
  }
}
