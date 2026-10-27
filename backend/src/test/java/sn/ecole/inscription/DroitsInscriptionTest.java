package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.CENSEUR_A;
import static sn.ecole.Fixtures.COMPTABLE_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ELEVE_A;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.PARENT_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;
import static sn.ecole.Fixtures.SURVEILLANT_A;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Matrice rôles × endpoints du module. Les identifiants visés n'existent pas : un rôle autorisé
 * obtient une erreur métier (404…) mais jamais 403 ; un rôle non autorisé obtient 403 avant tout
 * accès aux données. Un parent ou un élève n'a de droit que sur un élève qui lui est lié : sur un
 * identifiant quelconque, il est refusé.
 */
class DroitsInscriptionTest extends TestInscription {

  static final String DIRECTEUR = "DIRECTEUR";
  static final String CENSEUR = "CENSEUR";
  static final String SECRETARIAT = "SECRETARIAT";
  static final String SURVEILLANT = "SURVEILLANT";
  static final String ENSEIGNANT = "ENSEIGNANT";
  static final String COMPTABLE = "COMPTABLE";
  static final String PARENT = "PARENT";
  static final String ELEVE = "ELEVE";

  static final Map<String, UUID> UTILISATEURS = new LinkedHashMap<>();

  static {
    UTILISATEURS.put(DIRECTEUR, DIRECTEUR_A);
    UTILISATEURS.put(CENSEUR, CENSEUR_A);
    UTILISATEURS.put(SECRETARIAT, SECRETARIAT_A);
    UTILISATEURS.put(SURVEILLANT, SURVEILLANT_A);
    UTILISATEURS.put(ENSEIGNANT, ENSEIGNANT_A_PARENT_B);
    UTILISATEURS.put(COMPTABLE, COMPTABLE_A);
    UTILISATEURS.put(PARENT, PARENT_A);
    UTILISATEURS.put(ELEVE, ELEVE_A);
  }

  static final Set<String> ECRITURE = Set.of(DIRECTEUR, SECRETARIAT);
  static final Set<String> DIRECTION = Set.of(DIRECTEUR);
  static final Set<String> IDENTITE =
      Set.of(DIRECTEUR, SECRETARIAT, CENSEUR, SURVEILLANT, COMPTABLE);
  static final Set<String> SENSIBLE_LECTURE = Set.of(DIRECTEUR, CENSEUR);
  static final Set<String> SENSIBLE_ETAT = Set.of(DIRECTEUR, SECRETARIAT, CENSEUR);

  record Cas(String methode, String url, Object corps, Set<String> autorises) {

    @Override
    public String toString() {
      return methode + " " + url;
    }
  }

  static final UUID X = UUID.randomUUID();

  static List<Cas> cas() {
    Object eleve = identite("Awa", "Fictif");
    Object parametres = corps("formatMatricule", "{SEQ:4}", "politiqueCapacite", "AVERTIR");
    List<Cas> cas = new ArrayList<>();
    cas.add(new Cas("GET", ELEVES, null, IDENTITE));
    cas.add(new Cas("POST", ELEVES, eleve, ECRITURE));
    cas.add(new Cas("GET", ELEVES + "/" + X, null, IDENTITE));
    cas.add(new Cas("PUT", ELEVES + "/" + X, eleve, ECRITURE));
    cas.add(new Cas("DELETE", ELEVES + "/" + X, null, DIRECTION));
    cas.add(new Cas("DELETE", ELEVES + "/" + X + "/photo", null, ECRITURE));
    cas.add(new Cas("GET", ELEVES + "/" + X + "/donnees-sensibles", null, SENSIBLE_LECTURE));
    cas.add(new Cas("GET", ELEVES + "/" + X + "/donnees-sensibles/etat", null, SENSIBLE_ETAT));
    cas.add(new Cas("PUT", ELEVES + "/" + X + "/donnees-sensibles", corps(), ECRITURE));
    cas.add(new Cas("GET", "/api/v1/parametres-inscription", null, ECRITURE));
    cas.add(new Cas("PUT", "/api/v1/parametres-inscription", parametres, DIRECTION));
    Object responsable = corps("prenoms", "Mère", "nom", "Fictive", "telephone", "770000001");
    Object lien =
        corps(
            "responsableId",
            X,
            "type",
            "MERE",
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
    String r = "/api/v1/responsables";
    cas.add(new Cas("GET", r + "?nom=fictive", null, ECRITURE));
    cas.add(new Cas("POST", r, responsable, ECRITURE));
    cas.add(new Cas("GET", r + "/" + X, null, ECRITURE));
    cas.add(new Cas("PUT", r + "/" + X, responsable, ECRITURE));
    cas.add(new Cas("DELETE", r + "/" + X + "/compte", null, DIRECTION));
    cas.add(new Cas("POST", r + "/" + X + "/codes-activation", null, ECRITURE));
    String liens = ELEVES + "/" + X + "/responsables";
    cas.add(new Cas("GET", liens, null, Set.of(DIRECTEUR, SECRETARIAT, COMPTABLE)));
    cas.add(new Cas("POST", liens, lien, ECRITURE));
    cas.add(new Cas("PUT", liens + "/" + X, lien, ECRITURE));
    cas.add(
        new Cas("PUT", liens + "/" + X + "/acces-espace-parent", corps("acces", false), DIRECTION));
    cas.add(new Cas("DELETE", liens + "/" + X, null, ECRITURE));
    cas.add(
        new Cas(
            "GET", ELEVES + "/" + X + "/fratrie", null, Set.of(DIRECTEUR, SECRETARIAT, COMPTABLE)));
    cas.add(new Cas("GET", "/api/v1/moi/enfants", null, Set.of(PARENT)));
    Set<String> scolarite = Set.of(DIRECTEUR, SECRETARIAT, CENSEUR, SURVEILLANT);
    Object inscription =
        corps("eleveId", X, "classeId", X, "statut", "INSCRIT", "redoublant", false);
    Object sortie = corps("statut", "ABANDON", "date", "2026-11-02");
    Object radiation = corps("statut", "RADIE", "date", "2026-11-02");
    String i = "/api/v1/inscriptions";
    cas.add(new Cas("POST", i, inscription, ECRITURE));
    cas.add(new Cas("GET", i + "/" + X, null, scolarite));
    cas.add(new Cas("PUT", i + "/" + X, corps("redoublant", false), ECRITURE));
    cas.add(new Cas("POSTV", i + "/" + X + "/validation", null, ECRITURE));
    cas.add(new Cas("POSTV", i + "/" + X + "/sortie", sortie, ECRITURE));
    cas.add(new Cas("POSTV", i + "/" + X + "/sortie", radiation, DIRECTION));
    cas.add(new Cas("GET", ELEVES + "/" + X + "/inscriptions", null, scolarite));
    cas.add(new Cas("GET", "/api/v1/classes/" + X + "/eleves", null, scolarite));
    cas.add(new Cas("POST", ELEVES + "/" + X + "/codes-activation", null, ECRITURE));
    cas.add(new Cas("GET", "/api/v1/moi/eleve", null, Set.of(ELEVE)));
    return cas;
  }

  Reponse appeler(Cas c, UUID qui) throws Exception {
    return switch (c.methode()) {
      case "GET" -> get(qui, A, c.url());
      case "POST" -> post(qui, A, c.url(), c.corps());
      case "PUT" -> put(qui, A, c.url(), 99, c.corps());
      case "DELETE" -> delete(qui, A, c.url());
      case "POSTV" ->
          executer(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(c.url())
                  .header("If-Match", "\"99\"")
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content(
                      JSON.writeValueAsString(c.corps() == null ? java.util.Map.of() : c.corps())),
              qui,
              A);
      default -> throw new IllegalArgumentException(c.methode());
    };
  }

  @Test
  void chaqueEndpointRespecteLaMatriceDesDroits() throws Exception {
    List<String> ecarts = new ArrayList<>();
    for (Cas c : cas()) {
      for (Map.Entry<String, UUID> role : UTILISATEURS.entrySet()) {
        Reponse r = appeler(c, role.getValue());
        boolean autorise = c.autorises().contains(role.getKey());
        if (autorise && (r.statut() == 403 || r.statut() == 401)) {
          ecarts.add(c + " refusé à " + role.getKey() + " (" + r.statut() + " " + r.code() + ")");
        }
        if (!autorise && r.statut() != 403) {
          ecarts.add(
              c + " non refusé à " + role.getKey() + " (" + r.statut() + " " + r.code() + ")");
        }
      }
    }
    assertThat(ecarts).isEmpty();
  }

  @Test
  void sansJetonToutEstRefuse() throws Exception {
    var r =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ELEVES))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
  }
}
