package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.COMPTABLE_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.ELEVE_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/** Identité des élèves : matricule, recherche, correction, photo, isolement. */
class EleveApiTest extends TestInscription {

  private static final String ANNEE =
      String.valueOf(LocalDate.now(ZoneId.of("Africa/Dakar")).getYear());

  @Test
  void laCreationAttribueUnMatriculeSelonLeFormatParDefaut() throws Exception {
    Reponse r = post(SECRETARIAT_A, A, ELEVES, identite("Awa", "Fictif"));
    assertThat(r.statut()).isEqualTo(201);
    assertThat(r.corps().path("matricule").asText()).isEqualTo(ANNEE + "00001");
    assertThat(r.corps().path("nom").asText()).isEqualTo("FICTIF");
    assertThat(r.corps().path("compteActive").asBoolean()).isFalse();
    assertThat(r.brut().getHeader("Location")).isEqualTo(ELEVES + "/" + r.id());
    assertThat(r.brut().getHeader("Cache-Control")).isEqualTo("no-store");

    Reponse deuxieme = post(SECRETARIAT_A, A, ELEVES, identite("Binta", "Fictif"));
    assertThat(deuxieme.corps().path("matricule").asText()).isEqualTo(ANNEE + "00002");
    // Compteur propre à chaque école.
    Reponse autreEcole = post(DIRECTEUR_B, B, ELEVES, identite("Coumba", "Fictif"));
    assertThat(autreEcole.corps().path("matricule").asText()).isEqualTo(ANNEE + "00001");

    assertThat(journal("ELEVE_CREE")).hasSize(3);
  }

  @Test
  void leFormatDeLEcoleEtLesMatriculesDejaPrisSontRespectes() throws Exception {
    Reponse p = get(DIRECTEUR_A, A, "/api/v1/parametres-inscription");
    assertThat(p.corps().path("exempleMatricule").asText()).isEqualTo(ANNEE + "00001");
    Reponse m =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/parametres-inscription",
            versionEtag(p),
            corps("formatMatricule", "lya-{seq:3}", "politiqueCapacite", "BLOQUER"));
    assertThat(m.statut()).as(m.corps().toString()).isEqualTo(200);
    assertThat(m.corps().path("exempleMatricule").asText()).isEqualTo("LYA-001");

    // LYA-001 déjà pris (par exemple par une reprise de données) : il est sauté.
    UUID pris = creerEleve(A, "Awa");
    assertThat(get(DIRECTEUR_A, A, ELEVES + "/" + pris).corps().path("matricule").asText())
        .isEqualTo("LYA-001");
    superUtilisateur()
        .update("UPDATE ecole.sequence_matricule SET valeur = 0 WHERE tenant_id = ?", A);
    UUID suivant = creerEleve(A, "Binta");
    assertThat(get(DIRECTEUR_A, A, ELEVES + "/" + suivant).corps().path("matricule").asText())
        .isEqualTo("LYA-002");
  }

  @Test
  void seulLeDirecteurModifieLesParametresEtUnFormatInvalideEstRefuse() throws Exception {
    Object corps = corps("formatMatricule", "{SEQ:4}", "politiqueCapacite", "AVERTIR");
    assertThat(put(SECRETARIAT_A, A, "/api/v1/parametres-inscription", 0, corps).statut())
        .isEqualTo(403);
    Reponse invalide =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/parametres-inscription",
            0,
            corps("formatMatricule", "SANS-NUMERO", "politiqueCapacite", "AVERTIR"));
    assertThat(invalide.statut()).isEqualTo(400);
    assertThat(invalide.code()).isEqualTo("FORMAT_MATRICULE_INVALIDE");
    assertThat(put(DIRECTEUR_A, A, "/api/v1/parametres-inscription", 0, corps).statut())
        .isEqualTo(200);
    // Version périmée.
    assertThat(put(DIRECTEUR_A, A, "/api/v1/parametres-inscription", 0, corps).code())
        .isEqualTo("CONFLIT_VERSION");
  }

  @Test
  void desCreationsSimultaneesObtiennentDesMatriculesDistincts() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<Reponse>> envois = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        String prenom = "Eleve" + (char) ('a' + i);
        envois.add(pool.submit(() -> post(SECRETARIAT_A, A, ELEVES, identite(prenom, "Fictif"))));
      }
      Set<String> matricules = new HashSet<>();
      for (Future<Reponse> envoi : envois) {
        Reponse r = envoi.get();
        assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
        matricules.add(r.corps().path("matricule").asText());
      }
      assertThat(matricules).hasSize(16);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void laRechercheIgnoreLesAccentsEtPagineParCurseur() throws Exception {
    creerEleve(A, "Élodie");
    creerEleve(A, "Aminata");
    creerEleve(A, "Bineta");
    creerEleve(A, "Codou");
    creerEleve(A, "Dior");

    Reponse r = get(SECRETARIAT_A, A, ELEVES + "?recherche=ELODIE");
    assertThat(prenoms(r)).containsExactly("Élodie");
    assertThat(prenoms(get(SECRETARIAT_A, A, ELEVES + "?recherche=fictif b")))
        .containsExactly("Bineta");

    List<String> tous = new ArrayList<>();
    String curseur = null;
    int pages = 0;
    do {
      Reponse page =
          get(CENSEUR_A, A, ELEVES + "?taille=2" + (curseur == null ? "" : "&curseur=" + curseur));
      assertThat(page.statut()).isEqualTo(200);
      tous.addAll(prenoms(page));
      JsonNode suivant = page.corps().path("curseurSuivant");
      curseur = suivant.isNull() || suivant.isMissingNode() ? null : suivant.asText();
      pages++;
    } while (curseur != null);
    assertThat(pages).isEqualTo(3);
    assertThat(tous).containsExactly("Aminata", "Bineta", "Codou", "Dior", "Élodie");

    assertThat(get(SECRETARIAT_A, A, ELEVES + "?curseur=xyz").code()).isEqualTo("CURSEUR_INVALIDE");
  }

  @Test
  void laRechercheParMatriculeExact() throws Exception {
    creerEleve(A, "Awa");
    assertThat(prenoms(get(COMPTABLE_A, A, ELEVES + "?recherche=" + ANNEE + "00001")))
        .containsExactly("Awa");
    // Les jokers SQL sont des caractères ordinaires.
    assertThat(prenoms(get(COMPTABLE_A, A, ELEVES + "?recherche=%25"))).isEmpty();
  }

  @Test
  void laCorrectionDeLIdentiteExigeLaVersionLue() throws Exception {
    UUID eleve = creerEleve(A, "Awa");
    Reponse lu = get(SECRETARIAT_A, A, ELEVES + "/" + eleve);
    Map<String, Object> corrige = identite("Awa Marie", "Fictif");
    Reponse r = put(SECRETARIAT_A, A, ELEVES + "/" + eleve, lu.version(), corrige);
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("prenoms").asText()).isEqualTo("Awa Marie");
    assertThat(r.corps().path("matricule").asText())
        .isEqualTo(lu.corps().path("matricule").asText());
    assertThat(put(SECRETARIAT_A, A, ELEVES + "/" + eleve, lu.version(), corrige).code())
        .isEqualTo("CONFLIT_VERSION");
    assertThat(journal("ELEVE_MODIFIE"))
        .singleElement()
        .satisfies(j -> assertThat((String) j.get("details")).isEqualTo("{}"));
  }

  @Test
  void lesSaisiesInvalidesSontRefusees() throws Exception {
    Map<String, Object> futur = identite("Awa", "Fictif");
    futur.put("dateNaissance", LocalDate.now().plusDays(3).toString());
    assertThat(post(DIRECTEUR_A, A, ELEVES, futur).code()).isEqualTo("DATE_NAISSANCE_INVALIDE");

    Map<String, Object> sansSexe = identite("Awa", "Fictif");
    sansSexe.remove("sexe");
    assertThat(post(DIRECTEUR_A, A, ELEVES, sansSexe).code()).isEqualTo("VALIDATION");

    Map<String, Object> chiffres = identite("Awa", "F1ctif");
    Reponse r = post(DIRECTEUR_A, A, ELEVES, chiffres);
    assertThat(r.code()).isEqualTo("CHAMP_INVALIDE");
    // Le message ne recopie jamais la saisie.
    assertThat(r.corps().toString()).doesNotContain("F1ctif");
  }

  @Test
  void lIdentifiantNationalEstUniqueDansLEcole() throws Exception {
    Map<String, Object> avec = identite("Awa", "Fictif");
    avec.put("identifiantNational", "ID-0001");
    assertThat(post(DIRECTEUR_A, A, ELEVES, avec).statut()).isEqualTo(201);
    Map<String, Object> doublon = identite("Binta", "Fictif");
    doublon.put("identifiantNational", "id-0001");
    assertThat(post(DIRECTEUR_A, A, ELEVES, doublon).code())
        .isEqualTo("IDENTIFIANT_NATIONAL_EXISTANT");
    // Une autre école peut avoir le même (dossiers indépendants).
    assertThat(post(DIRECTEUR_B, B, ELEVES, avec).statut()).isEqualTo(201);
  }

  @Test
  void lEcoleANeVoitNiNeModifieLesElevesDeLEcoleB() throws Exception {
    UUID eleveB = creerEleve(B, "Coumba");
    assertThat(get(DIRECTEUR_A, A, ELEVES + "/" + eleveB).code()).isEqualTo("ELEVE_INTROUVABLE");
    assertThat(put(DIRECTEUR_A, A, ELEVES + "/" + eleveB, 0, identite("X", "Y")).statut())
        .isEqualTo(404);
    assertThat(delete(DIRECTEUR_A, A, ELEVES + "/" + eleveB).statut()).isEqualTo(404);
    assertThat(prenoms(get(DIRECTEUR_A, A, ELEVES))).isEmpty();
    // Le directeur de B, sans rôle dans A, n'y entre pas.
    assertThat(get(DIRECTEUR_B, A, ELEVES).code()).isEqualTo("TENANT_NON_AUTORISE");
  }

  @Test
  void laPhotoEstControleeRemplaceeEtServieParUrlSignee() throws Exception {
    UUID eleve = creerEleve(A, "Awa");
    String url = ELEVES + "/" + eleve + "/photo";
    Reponse refus =
        multipart(SECRETARIAT_A, A, HttpMethod.PUT, url, "fichier", "%PDF-1.7".getBytes());
    assertThat(refus.code()).isEqualTo("FICHIER_INVALIDE");

    Reponse r = multipart(SECRETARIAT_A, A, HttpMethod.PUT, url, "fichier", png(120, 160));
    assertThat(r.statut()).isEqualTo(200);
    String premiere = r.corps().path("photoUrl").asText();
    assertThat(premiere).startsWith("https://stockage.test/" + A + "/eleves/" + eleve + "/photo/");
    assertThat(stockage.objets()).hasSize(1);

    multipart(SECRETARIAT_A, A, HttpMethod.PUT, url, "fichier", png(100, 100));
    assertThat(stockage.objets()).hasSize(1); // l'ancienne est supprimée après validation

    Reponse lu = get(CENSEUR_A, A, ELEVES + "/" + eleve);
    assertThat(lu.corps().path("photoUrl").asText()).contains("expire=300");

    assertThat(delete(SECRETARIAT_A, A, url).statut()).isEqualTo(200);
    assertThat(stockage.objets()).isEmpty();
    assertThat(delete(SECRETARIAT_A, A, url).code()).isEqualTo("PHOTO_INTROUVABLE");
  }

  @Test
  void seulLeDirecteurSupprimeUnEleveEtLeMatriculeNEstJamaisReutilise() throws Exception {
    UUID eleve = creerEleve(A, "Awa");
    assertThat(delete(SECRETARIAT_A, A, ELEVES + "/" + eleve).statut()).isEqualTo(403);
    assertThat(delete(DIRECTEUR_A, A, ELEVES + "/" + eleve).statut()).isEqualTo(204);
    assertThat(get(DIRECTEUR_A, A, ELEVES + "/" + eleve).statut()).isEqualTo(404);
    superUtilisateur()
        .update("UPDATE ecole.sequence_matricule SET valeur = 0 WHERE tenant_id = ?", A);
    UUID autre = creerEleve(A, "Binta");
    assertThat(get(DIRECTEUR_A, A, ELEVES + "/" + autre).corps().path("matricule").asText())
        .isEqualTo(ANNEE + "00002");
  }

  @Test
  void unEleveNeVoitQueLuiMeme() throws Exception {
    UUID moi = creerEleve(A, "Awa");
    UUID autre = creerEleve(A, "Binta");
    relierCompteEleve(moi, ELEVE_A);

    Reponse r = get(ELEVE_A, A, ELEVES + "/" + moi);
    assertThat(r.statut()).isEqualTo(200);
    assertThat(r.corps().path("compteActive").asBoolean()).isTrue();
    assertThat(r.corps().has("utilisateurId")).isFalse();
    assertThat(get(ELEVE_A, A, ELEVES + "/" + autre).statut()).isEqualTo(403);
    assertThat(get(ELEVE_A, A, ELEVES).statut()).isEqualTo(403);
  }

  private static List<String> prenoms(Reponse r) {
    List<String> prenoms = new ArrayList<>();
    for (JsonNode e : r.corps().path("elements")) {
      prenoms.add(e.path("prenoms").asText());
    }
    return prenoms;
  }
}
