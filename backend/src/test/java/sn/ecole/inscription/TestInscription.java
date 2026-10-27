package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import sn.ecole.etablissement.TestEtablissement;

/**
 * Base des tests du module inscription : structure des écoles A et B (via TestEtablissement),
 * tables du module vidées avant chaque test. Toutes les identités sont manifestement fictives.
 */
public abstract class TestInscription extends TestEtablissement {

  protected static final String ELEVES = "/api/v1/eleves";

  @BeforeEach
  void viderInscription() {
    superUtilisateur()
        .execute(
            """
            TRUNCATE ecole.inscription_option, ecole.inscription, ecole.code_activation,
              ecole.lien_parental, ecole.responsable,
              ecole.eleve_dossier_sensible, ecole.eleve, ecole.sequence_matricule,
              ecole.parametres_inscription, ecole.journal_audit CASCADE""");
  }

  /** Identité fictive valide ; {@code prenoms} distingue les élèves d'un même test. */
  protected static Map<String, Object> identite(String prenoms, String nom) {
    return corps(
        "prenoms",
        prenoms,
        "nom",
        nom,
        "sexe",
        "F",
        "dateNaissance",
        LocalDate.of(2012, 5, 14).toString(),
        "lieuNaissance",
        "Ville Fictive",
        "nationalite",
        "SN");
  }

  protected UUID creerEleve(UUID qui, UUID ecole, String prenoms, String nom) throws Exception {
    Reponse r = post(qui, ecole, ELEVES, identite(prenoms, nom));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  protected UUID creerEleve(UUID ecole, String prenoms) throws Exception {
    return creerEleve(
        ecole.equals(A) ? DIRECTEUR_A : sn.ecole.Fixtures.DIRECTEUR_B, ecole, prenoms, "Fictif");
  }

  protected Reponse multipart(
      UUID utilisateur, UUID ecole, HttpMethod methode, String url, String partie, byte[] octets)
      throws Exception {
    return executer(
        MockMvcRequestBuilders.multipart(methode, url)
            .file(new MockMultipartFile(partie, "f", "application/octet-stream", octets)),
        utilisateur,
        ecole);
  }

  /** Entrées du journal d'audit pour une action, dans l'ordre. */
  protected List<Map<String, Object>> journal(String action) {
    return superUtilisateur()
        .queryForList(
            "SELECT tenant_id, acteur_id, id_cible, details::text AS details FROM"
                + " ecole.journal_audit WHERE action = ? ORDER BY cree_le",
            action);
  }

  protected UUID creerResponsable(UUID qui, UUID ecole, String prenoms, String telephone)
      throws Exception {
    Reponse r =
        post(
            qui,
            ecole,
            "/api/v1/responsables",
            corps("prenoms", prenoms, "nom", "Parent-Fictif", "telephone", telephone));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  /** Lien avec tous les droits ; renvoie l'id du lien. */
  protected UUID lier(
      UUID qui, UUID ecole, UUID eleve, UUID responsable, String type, boolean payeur)
      throws Exception {
    Reponse r =
        post(
            qui,
            ecole,
            ELEVES + "/" + eleve + "/responsables",
            corps(
                "responsableId",
                responsable,
                "type",
                type,
                "responsableLegal",
                true,
                "payeur",
                payeur,
                "contactUrgence",
                true,
                "autoriseRecuperer",
                true,
                "recoitNotifications",
                true));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  /** Relie un compte au responsable et lui donne le rôle PARENT, comme l'activation par code. */
  protected void relierCompteParent(UUID ecole, UUID responsable, UUID utilisateur) {
    superUtilisateur()
        .update(
            "UPDATE ecole.responsable SET utilisateur_id = ? WHERE id = ?",
            utilisateur,
            responsable);
    sn.ecole.Fixtures.appartenance(superUtilisateur(), ecole, utilisateur, "PARENT");
  }

  /** Version portée par l'en-tête ETag (« "3" » donne 3). */
  protected static int versionEtag(Reponse r) {
    return Integer.parseInt(r.etag().replace("\"", ""));
  }

  /** Relie un compte (Keycloak) à l'élève, comme le fera l'activation par code. */
  protected void relierCompteEleve(UUID eleve, UUID utilisateur) {
    superUtilisateur()
        .update("UPDATE ecole.eleve SET utilisateur_id = ? WHERE id = ?", utilisateur, eleve);
  }
}
