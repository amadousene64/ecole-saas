package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.COMPTABLE_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.SECRETARIAT_A;
import static sn.ecole.Fixtures.SURVEILLANT_A;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.ecole.shared.application.ExecutionPourTenant;
import tools.jackson.databind.JsonNode;

/** Responsables, liens parentaux, droits de lecture par rôle, IDOR, fratrie. */
class ResponsablesApiTest extends TestInscription {

  private static final String RESPONSABLES = "/api/v1/responsables";

  @Autowired sn.ecole.inscription.ContactsEleve contacts;
  @Autowired ExecutionPourTenant execution;

  /** Deux comptes parents fictifs, sans rôle avant le test. */
  private final UUID compteMere = UUID.randomUUID();

  private final UUID comptePere = UUID.randomUUID();

  private UUID awa;
  private UUID binta;
  private UUID mere;
  private UUID pere;
  private UUID lienMereAwa;
  private UUID lienPereAwa;

  @BeforeEach
  void famille() throws Exception {
    awa = creerEleve(A, "Awa");
    binta = creerEleve(A, "Binta");
    mere = creerResponsable(SECRETARIAT_A, A, "Mère", "770000001");
    pere = creerResponsable(SECRETARIAT_A, A, "Père", "770000002");
    lienMereAwa = lier(SECRETARIAT_A, A, awa, mere, "MERE", false);
    lienPereAwa = lier(SECRETARIAT_A, A, awa, pere, "PERE", true);
    lier(SECRETARIAT_A, A, binta, mere, "MERE", true);
    relierCompteParent(A, mere, compteMere);
    relierCompteParent(A, pere, comptePere);
  }

  private static List<String> prenomsResponsables(Reponse r) {
    List<String> prenoms = new ArrayList<>();
    for (JsonNode l : r.corps()) {
      prenoms.add(l.path("responsable").path("prenoms").asText());
    }
    return prenoms;
  }

  private static List<String> prenomsEleves(Reponse r) {
    List<String> prenoms = new ArrayList<>();
    for (JsonNode e : r.corps()) {
      prenoms.add(e.path("prenoms").asText());
    }
    return prenoms;
  }

  @Test
  void leResponsableEstNormaliseEtRecherchableParTelephoneOuNom() throws Exception {
    Reponse lu = get(SECRETARIAT_A, A, RESPONSABLES + "/" + mere);
    assertThat(lu.corps().path("telephone").asText()).isEqualTo("+221770000001");
    assertThat(lu.corps().path("compteActive").asBoolean()).isTrue();
    assertThat(lu.corps().has("utilisateurId")).isFalse();

    assertThat(get(SECRETARIAT_A, A, RESPONSABLES + "?telephone=77 000 00 01").corps())
        .singleElement()
        .satisfies(r -> assertThat(r.path("id").asText()).isEqualTo(mere.toString()));
    assertThat(get(SECRETARIAT_A, A, RESPONSABLES + "?nom=pere").corps()).hasSize(1);
    assertThat(get(SECRETARIAT_A, A, RESPONSABLES).code()).isEqualTo("CRITERE_REQUIS");
    assertThat(get(COMPTABLE_A, A, RESPONSABLES + "?nom=pere").statut()).isEqualTo(403);
  }

  @Test
  void laModificationExigeLaVersionEtLeTelephoneEstControle() throws Exception {
    Reponse lu = get(SECRETARIAT_A, A, RESPONSABLES + "/" + pere);
    Object corps =
        corps("prenoms", "Père", "nom", "Parent-Fictif", "telephone", "+33 6 00 00 00 00");
    Reponse r = put(SECRETARIAT_A, A, RESPONSABLES + "/" + pere, lu.version(), corps);
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("telephone").asText()).isEqualTo("+33600000000");
    assertThat(put(SECRETARIAT_A, A, RESPONSABLES + "/" + pere, lu.version(), corps).code())
        .isEqualTo("CONFLIT_VERSION");
    Object faux = corps("prenoms", "Père", "nom", "Parent-Fictif", "telephone", "abc");
    Reponse refus = put(SECRETARIAT_A, A, RESPONSABLES + "/" + pere, r.version(), faux);
    assertThat(refus.code()).isEqualTo("TELEPHONE_INVALIDE");
  }

  @Test
  void laDirectionVoitTousLesResponsablesLeComptableLesPayeurs() throws Exception {
    assertThat(prenomsResponsables(get(DIRECTEUR_A, A, ELEVES + "/" + awa + "/responsables")))
        .containsExactlyInAnyOrder("Mère", "Père");
    assertThat(prenomsResponsables(get(COMPTABLE_A, A, ELEVES + "/" + awa + "/responsables")))
        .containsExactly("Père");
    assertThat(get(SURVEILLANT_A, A, ELEVES + "/" + awa + "/responsables").statut()).isEqualTo(403);
  }

  @Test
  void unParentNeVoitQueSonLienJamaisLAutreParent() throws Exception {
    Reponse r = get(compteMere, A, ELEVES + "/" + awa + "/responsables");
    assertThat(r.statut()).isEqualTo(200);
    assertThat(prenomsResponsables(r)).containsExactly("Mère");
    assertThat(r.brut().getContentAsString()).doesNotContain("770000002");
  }

  @Test
  void idorUnParentNeLitPasLEnfantDUnAutre() throws Exception {
    // Le père n'est lié qu'à Awa : Binta (fille de la mère seulement) lui est inaccessible.
    assertThat(get(comptePere, A, ELEVES + "/" + awa).statut()).isEqualTo(200);
    assertThat(get(comptePere, A, ELEVES + "/" + binta).statut()).isEqualTo(403);
    assertThat(get(comptePere, A, ELEVES + "/" + binta + "/responsables").statut()).isEqualTo(403);
    assertThat(get(comptePere, A, ELEVES + "/" + binta + "/donnees-sensibles").statut())
        .isEqualTo(403);
    assertThat(get(comptePere, A, ELEVES).statut()).isEqualTo(403);
    assertThat(get(comptePere, A, RESPONSABLES + "/" + mere).statut()).isEqualTo(403);
    assertThat(prenomsEleves(get(comptePere, A, "/api/v1/moi/enfants"))).containsExactly("Awa");
    assertThat(prenomsEleves(get(compteMere, A, "/api/v1/moi/enfants")))
        .containsExactly("Awa", "Binta");
    // Un parent ne crée jamais de lien lui-même.
    Object lien =
        corps(
            "responsableId",
            pere,
            "type",
            "PERE",
            "responsableLegal",
            true,
            "payeur",
            true,
            "contactUrgence",
            true,
            "autoriseRecuperer",
            true,
            "recoitNotifications",
            true);
    assertThat(post(comptePere, A, ELEVES + "/" + binta + "/responsables", lien).statut())
        .isEqualTo(403);
  }

  @Test
  void sansAccesEspaceParentLeParentNeVoitPlusLEnfantNiNEstNotifie() throws Exception {
    Reponse lien = get(DIRECTEUR_A, A, ELEVES + "/" + awa + "/responsables");
    int version = 0;
    for (JsonNode l : lien.corps()) {
      if (l.path("id").asText().equals(lienPereAwa.toString())) {
        version = l.path("version").asInt();
      }
    }
    String url = ELEVES + "/" + awa + "/responsables/" + lienPereAwa + "/acces-espace-parent";
    assertThat(put(SECRETARIAT_A, A, url, version, corps("acces", false)).statut()).isEqualTo(403);
    Reponse r = put(DIRECTEUR_A, A, url, version, corps("acces", false));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    assertThat(r.corps().path("accesEspaceParent").asBoolean()).isFalse();
    assertThat(r.corps().path("recoitNotifications").asBoolean()).isFalse();

    assertThat(get(comptePere, A, ELEVES + "/" + awa).statut()).isEqualTo(403);
    assertThat(get(comptePere, A, ELEVES + "/" + awa + "/responsables").statut()).isEqualTo(403);
    assertThat(prenomsEleves(get(comptePere, A, "/api/v1/moi/enfants"))).isEmpty();
    var destinataires = execution.lire(A, () -> contacts.destinatairesNotifications(awa));
    assertThat(destinataires).extracting(d -> d.responsableId()).containsExactly(mere);
    assertThat(execution.lire(A, () -> contacts.estParentAutorise(comptePere, awa))).isFalse();
    assertThat(execution.lire(A, () -> contacts.estParentAutorise(compteMere, awa))).isTrue();

    // Le secrétariat ne peut pas lui redonner les notifications par la modification ordinaire.
    Object droits =
        corps(
            "type",
            "PERE",
            "responsableLegal",
            true,
            "payeur",
            true,
            "contactUrgence",
            true,
            "autoriseRecuperer",
            true,
            "recoitNotifications",
            true);
    assertThat(
            put(
                    SECRETARIAT_A,
                    A,
                    ELEVES + "/" + awa + "/responsables/" + lienPereAwa,
                    r.version(),
                    droits)
                .code())
        .isEqualTo("NOTIFICATIONS_INTERDITES");

    assertThat(journal("ACCES_ESPACE_PARENT_MODIFIE"))
        .singleElement()
        .satisfies(
            j -> {
              assertThat(j.get("acteur_id")).isEqualTo(DIRECTEUR_A);
              assertThat((String) j.get("details")).contains("\"acces\": false");
            });
  }

  @Test
  void unLienEnDoubleEstRefuseEtLeDernierResponsableLegalReste() throws Exception {
    Object doublon =
        corps(
            "responsableId",
            mere,
            "type",
            "MERE",
            "responsableLegal",
            true,
            "payeur",
            false,
            "contactUrgence",
            true,
            "autoriseRecuperer",
            true,
            "recoitNotifications",
            true);
    assertThat(post(SECRETARIAT_A, A, ELEVES + "/" + awa + "/responsables", doublon).code())
        .isEqualTo("LIEN_EXISTANT");

    assertThat(
            delete(SECRETARIAT_A, A, ELEVES + "/" + awa + "/responsables/" + lienMereAwa).statut())
        .isEqualTo(204);
    assertThat(delete(SECRETARIAT_A, A, ELEVES + "/" + awa + "/responsables/" + lienPereAwa).code())
        .isEqualTo("DERNIER_RESPONSABLE_LEGAL");
    // Un lien d'un autre élève ne se manipule pas par cette URL.
    assertThat(
            delete(SECRETARIAT_A, A, ELEVES + "/" + binta + "/responsables/" + lienPereAwa)
                .statut())
        .isEqualTo(404);
  }

  @Test
  void laFratrieSeDeduitDesResponsablesCommuns() throws Exception {
    UUID coumba = creerEleve(A, "Coumba");
    assertThat(prenomsEleves(get(SECRETARIAT_A, A, ELEVES + "/" + awa + "/fratrie")))
        .containsExactly("Binta");
    assertThat(prenomsEleves(get(COMPTABLE_A, A, ELEVES + "/" + binta + "/fratrie")))
        .containsExactly("Awa");
    assertThat(get(COMPTABLE_A, A, ELEVES + "/" + coumba + "/fratrie").corps()).isEmpty();
    assertThat(get(SURVEILLANT_A, A, ELEVES + "/" + awa + "/fratrie").statut()).isEqualTo(403);
    assertThat(get(compteMere, A, ELEVES + "/" + awa + "/fratrie").statut()).isEqualTo(403);
  }

  @Test
  void unEleveAvecDesResponsablesNeSeSupprimePas() throws Exception {
    assertThat(delete(DIRECTEUR_A, A, ELEVES + "/" + awa).code()).isEqualTo("ELEVE_UTILISE");
  }

  @Test
  void lEcoleBNeVoitNiLesResponsablesNiLesLiensDeLEcoleA() throws Exception {
    assertThat(get(DIRECTEUR_B, B, RESPONSABLES + "/" + mere).code())
        .isEqualTo("RESPONSABLE_INTROUVABLE");
    assertThat(get(DIRECTEUR_B, B, RESPONSABLES + "?telephone=770000001").corps()).isEmpty();
    assertThat(get(DIRECTEUR_B, B, ELEVES + "/" + awa + "/responsables").statut()).isEqualTo(404);
    UUID eleveB = creerEleve(B, "Dior");
    Object lienVersA =
        corps(
            "responsableId",
            mere,
            "type",
            "MERE",
            "responsableLegal",
            true,
            "payeur",
            false,
            "contactUrgence",
            true,
            "autoriseRecuperer",
            true,
            "recoitNotifications",
            true);
    assertThat(post(DIRECTEUR_B, B, ELEVES + "/" + eleveB + "/responsables", lienVersA).code())
        .isEqualTo("RESPONSABLE_INTROUVABLE");
    // Le compte de la mère n'a aucun rôle dans B.
    assertThat(get(compteMere, B, "/api/v1/moi/enfants").code()).isEqualTo("TENANT_NON_AUTORISE");
  }

  @Test
  void leDirecteurDetacheUnCompteParent() throws Exception {
    assertThat(delete(SECRETARIAT_A, A, RESPONSABLES + "/" + pere + "/compte").statut())
        .isEqualTo(403);
    Reponse r = delete(DIRECTEUR_A, A, RESPONSABLES + "/" + pere + "/compte");
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("compteActive").asBoolean()).isFalse();
    // Plus membre de l'école : plus d'accès.
    assertThat(get(comptePere, A, ELEVES + "/" + awa).code()).isEqualTo("TENANT_NON_AUTORISE");
    assertThat(delete(DIRECTEUR_A, A, RESPONSABLES + "/" + pere + "/compte").code())
        .isEqualTo("COMPTE_NON_RELIE");
  }
}
