package sn.ecole.etablissement;

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
 * Matrice rôles × endpoints. Chaque appel porte un corps valide et vise des identifiants qui
 * n'existent pas : un rôle autorisé obtient une erreur métier (404, 409…) mais jamais 403, un rôle
 * non autorisé obtient 403 avant tout accès aux données.
 */
class DroitsParRoleTest extends TestEtablissement {

  private static final String DIRECTEUR = "DIRECTEUR";
  private static final String CENSEUR = "CENSEUR";
  private static final String SECRETARIAT = "SECRETARIAT";
  private static final String SURVEILLANT = "SURVEILLANT";
  private static final String ENSEIGNANT = "ENSEIGNANT";
  private static final String COMPTABLE = "COMPTABLE";
  private static final String PARENT = "PARENT";
  private static final String ELEVE = "ELEVE";

  private static final Map<String, UUID> UTILISATEURS = new LinkedHashMap<>();

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

  private static final Set<String> TOUS = Set.copyOf(UTILISATEURS.keySet());
  private static final Set<String> PERSONNEL =
      Set.of(DIRECTEUR, CENSEUR, SECRETARIAT, SURVEILLANT, ENSEIGNANT, COMPTABLE);
  private static final Set<String> ECRITURE = Set.of(DIRECTEUR, SECRETARIAT);
  private static final Set<String> DIRECTION = Set.of(DIRECTEUR);

  private record Cas(String methode, String url, Object corps, Set<String> autorises) {

    @Override
    public String toString() {
      return methode + " " + url;
    }
  }

  private static final UUID X = UUID.randomUUID();
  private static final UUID Y = UUID.randomUUID();
  private static final String ANNEES = "/api/v1/annees-scolaires/";

  private static List<Cas> cas() {
    Object profil = corps("nom", "Lycée", "code", "LYC", "statut", "PUBLIC");
    Object niveau = corps("cycleId", X, "code", "ZZ", "libelle", "Zz", "ordre", 1);
    Object modifNiveau = corps("libelle", "Zz", "ordre", 1, "actif", true);
    Object serie = corps("code", "ZZ", "libelle", "Zz");
    Object annee =
        corps("libelle", "2026-2027", "dateDebut", "2026-10-05", "dateFin", "2027-07-02");
    Object periode = corps("libelle", "P", "dateDebut", "2026-10-05", "dateFin", "2026-12-01");
    Object evenement =
        corps(
            "type",
            "FERIE",
            "libelle",
            "Férié",
            "dateDebut",
            "2026-11-01",
            "dateFin",
            "2026-11-01");
    Object classe = corps("anneeId", X, "niveauId", X, "nom", "ZZ", "capacite", 30);
    Object salle = corps("nom", "Salle Z", "capacite", 30);
    Object cycle =
        corps(
            "libelle",
            "C",
            "typePeriode",
            "SEMESTRE",
            "modeNotation",
            "COEFFICIENT",
            "baseMoyenne",
            20);

    List<Cas> cas = new ArrayList<>();
    cas.add(new Cas("GET", "/api/v1/etablissement", null, TOUS));
    cas.add(new Cas("PUT", "/api/v1/etablissement", profil, ECRITURE));
    cas.add(new Cas("DELETE", "/api/v1/etablissement/images/LOGO", null, ECRITURE));
    cas.add(new Cas("DELETE", "/api/v1/etablissement/images/CACHET", null, DIRECTION));
    cas.add(new Cas("DELETE", "/api/v1/etablissement/images/SIGNATURE", null, DIRECTION));
    cas.add(new Cas("GET", "/api/v1/cycles", null, PERSONNEL));
    cas.add(new Cas("PUT", "/api/v1/cycles/" + X, cycle, DIRECTION));
    cas.add(new Cas("GET", "/api/v1/niveaux", null, PERSONNEL));
    cas.add(new Cas("GET", "/api/v1/niveaux/" + X, null, PERSONNEL));
    cas.add(new Cas("POST", "/api/v1/niveaux", niveau, ECRITURE));
    cas.add(new Cas("PUT", "/api/v1/niveaux/" + X, modifNiveau, ECRITURE));
    cas.add(
        new Cas("PUT", "/api/v1/niveaux/" + X + "/series", corps("serieIds", List.of()), ECRITURE));
    cas.add(new Cas("DELETE", "/api/v1/niveaux/" + X, null, ECRITURE));
    cas.add(new Cas("GET", "/api/v1/series", null, PERSONNEL));
    cas.add(new Cas("POST", "/api/v1/series", serie, ECRITURE));
    cas.add(new Cas("PUT", "/api/v1/series/" + X, serie, ECRITURE));
    cas.add(new Cas("DELETE", "/api/v1/series/" + X, null, ECRITURE));
    cas.add(new Cas("GET", ANNEES.substring(0, ANNEES.length() - 1), null, TOUS));
    cas.add(new Cas("GET", ANNEES + "en-cours", null, TOUS));
    cas.add(new Cas("GET", ANNEES + X, null, TOUS));
    cas.add(new Cas("POST", ANNEES.substring(0, ANNEES.length() - 1), annee, ECRITURE));
    cas.add(new Cas("PUT", ANNEES + X, annee, ECRITURE));
    cas.add(new Cas("DELETE", ANNEES + X, null, ECRITURE));
    cas.add(new Cas("POST", ANNEES + X + "/ouverture", null, DIRECTION));
    cas.add(new Cas("POST", ANNEES + X + "/cloture", null, DIRECTION));
    cas.add(new Cas("GET", ANNEES + X + "/periodes", null, TOUS));
    cas.add(new Cas("POST", ANNEES + X + "/periodes/generation", null, ECRITURE));
    cas.add(new Cas("PUT", ANNEES + X + "/periodes/" + Y, periode, ECRITURE));
    cas.add(new Cas("GET", ANNEES + X + "/calendrier", null, TOUS));
    cas.add(new Cas("POST", ANNEES + X + "/calendrier", evenement, ECRITURE));
    cas.add(new Cas("PUT", ANNEES + X + "/calendrier/" + Y, evenement, ECRITURE));
    cas.add(new Cas("DELETE", ANNEES + X + "/calendrier/" + Y, null, ECRITURE));
    cas.add(new Cas("GET", "/api/v1/classes", null, PERSONNEL));
    cas.add(new Cas("GET", "/api/v1/classes/" + X, null, PERSONNEL));
    cas.add(new Cas("POST", "/api/v1/classes", classe, ECRITURE));
    cas.add(new Cas("PUT", "/api/v1/classes/" + X, classe, ECRITURE));
    cas.add(new Cas("DELETE", "/api/v1/classes/" + X, null, ECRITURE));
    cas.add(new Cas("GET", "/api/v1/salles", null, PERSONNEL));
    cas.add(new Cas("GET", "/api/v1/salles/" + X, null, PERSONNEL));
    cas.add(new Cas("POST", "/api/v1/salles", salle, ECRITURE));
    cas.add(new Cas("PUT", "/api/v1/salles/" + X, salle, ECRITURE));
    cas.add(new Cas("DELETE", "/api/v1/salles/" + X, null, ECRITURE));
    Object option = corps("code", "ESP", "libelle", "Espagnol", "groupe", "LV2");
    cas.add(new Cas("GET", "/api/v1/options-enseignement", null, PERSONNEL));
    cas.add(new Cas("POST", "/api/v1/options-enseignement", option, ECRITURE));
    cas.add(new Cas("PUT", "/api/v1/options-enseignement/" + X, option, ECRITURE));
    cas.add(new Cas("DELETE", "/api/v1/options-enseignement/" + X, null, ECRITURE));
    cas.add(new Cas("GET", "/api/v1/classes/" + X + "/options", null, PERSONNEL));
    cas.add(
        new Cas(
            "PUT", "/api/v1/classes/" + X + "/options", corps("optionIds", List.of()), ECRITURE));
    return cas;
  }

  private Reponse appeler(Cas c, UUID utilisateur) throws Exception {
    return switch (c.methode()) {
      case "GET" -> get(utilisateur, A, c.url());
      case "POST" -> post(utilisateur, A, c.url(), c.corps());
      case "PUT" -> put(utilisateur, A, c.url(), 0, c.corps());
      case "DELETE" -> delete(utilisateur, A, c.url());
      default -> throw new IllegalArgumentException(c.methode());
    };
  }

  @Test
  void chaqueEndpointRespecteLaMatriceDesDroits() throws Exception {
    List<String> ecarts = new ArrayList<>();
    int appels = 0;
    for (Cas c : cas()) {
      for (Map.Entry<String, UUID> role : UTILISATEURS.entrySet()) {
        Reponse r = appeler(c, role.getValue());
        appels++;
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
    assertThat(ecarts).as("écarts sur %s appels", appels).isEmpty();
    assertThat(appels).isGreaterThan(300);
  }

  @Test
  void lesImagesSensiblesSuiventLaMatrice() throws Exception {
    byte[] image = png(40, 40);
    Map<String, Set<String>> attendu =
        Map.of("LOGO", ECRITURE, "CACHET", DIRECTION, "SIGNATURE", DIRECTION);
    List<String> ecarts = new ArrayList<>();
    for (Map.Entry<String, Set<String>> type : attendu.entrySet()) {
      for (Map.Entry<String, UUID> role : UTILISATEURS.entrySet()) {
        Reponse r = televerser(role.getValue(), A, type.getKey(), image, "i.png");
        boolean autorise = type.getValue().contains(role.getKey());
        if (autorise != (r.statut() == 200)) {
          ecarts.add(type.getKey() + " par " + role.getKey() + " : " + r.statut());
        }
      }
    }
    assertThat(ecarts).isEmpty();
  }

  @Test
  void unRoleDUneAutreEcoleNAAucunDroitIci() throws Exception {
    // Enseignant de A mais PARENT de B : dans B, il n'a que les droits d'un parent.
    assertThat(get(ENSEIGNANT_A_PARENT_B, B, "/api/v1/cycles").statut()).isEqualTo(403);
    assertThat(get(ENSEIGNANT_A_PARENT_B, B, "/api/v1/annees-scolaires").statut()).isEqualTo(200);
    assertThat(get(ENSEIGNANT_A_PARENT_B, A, "/api/v1/cycles").statut()).isEqualTo(200);
  }
}
