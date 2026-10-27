package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Isolement des écoles au niveau de PostgreSQL, avec le rôle applicatif (soumis à la RLS) : même
 * une requête fautive de l'application ne peut ni lire ni écrire chez une autre école.
 */
class IsolementBaseTest extends TestEtablissement {

  private static final List<String> TABLES =
      List.of(
          "etablissement",
          "etablissement_image",
          "cycle",
          "niveau",
          "serie",
          "niveau_serie",
          "annee_scolaire",
          "periode",
          "evenement_calendrier",
          "salle",
          "classe",
          "parametres_cycle_annee",
          "parametres_niveau_annee");

  private UUID anneeA;
  private UUID anneeB;
  private UUID classeA;
  private UUID niveauA;

  @BeforeEach
  void donnees() throws Exception {
    anneeA = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    anneeB = creerEtOuvrirAnnee(DIRECTEUR_B, B);
    niveauA = niveau(DIRECTEUR_A, A, "TLE");
    classeA =
        post(
                DIRECTEUR_A,
                A,
                "/api/v1/classes",
                corps(
                    "anneeId",
                    anneeA,
                    "niveauId",
                    niveauA,
                    "serieId",
                    serie(DIRECTEUR_A, A, "S2"),
                    "nom",
                    "TS2B",
                    "capacite",
                    40))
            .id();
    post(
        DIRECTEUR_B,
        B,
        "/api/v1/classes",
        corps(
            "anneeId",
            anneeB,
            "niveauId",
            niveau(DIRECTEUR_B, B, "CP"),
            "nom",
            "CPA",
            "capacite",
            30));
    post(DIRECTEUR_A, A, "/api/v1/salles", corps("nom", "Salle A", "capacite", 40));
    post(DIRECTEUR_B, B, "/api/v1/salles", corps("nom", "Salle B", "capacite", 40));
    televerser(DIRECTEUR_A, A, "LOGO", png(32, 32), "a.png");
    televerser(DIRECTEUR_B, B, "LOGO", png(32, 32), "b.png");
  }

  /**
   * Exécute {@code action} sur une connexion du rôle applicatif placée dans l'école {@code ecole}.
   */
  private static <T> T dansEcole(UUID ecole, ConnectionCallback<T> action) {
    JdbcTemplate jdbc = roleApplicatif();
    return jdbc.execute(
        (ConnectionCallback<T>)
            connexion -> {
              try (PreparedStatement ps =
                  connexion.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                ps.setString(1, ecole == null ? "" : ecole.toString());
                ps.execute();
              }
              return action.doInConnection(connexion);
            });
  }

  private static int compter(UUID ecole, String sql) {
    return dansEcole(
        ecole,
        connexion -> {
          try (PreparedStatement ps = connexion.prepareStatement(sql);
              ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
          }
        });
  }

  private static int modifier(UUID ecole, String sql, Object... parametres) {
    return dansEcole(
        ecole,
        connexion -> {
          try (PreparedStatement ps = connexion.prepareStatement(sql)) {
            for (int i = 0; i < parametres.length; i++) {
              ps.setObject(i + 1, parametres[i]);
            }
            return ps.executeUpdate();
          }
        });
  }

  @Test
  void chaqueEcoleNeVoitQueSesLignesDansChaqueTable() {
    for (String table : TABLES) {
      int total =
          superUtilisateur().queryForObject("SELECT count(*) FROM ecole." + table, Integer.class);
      int chezA = compter(A, "SELECT count(*) FROM " + table);
      int chezB = compter(B, "SELECT count(*) FROM " + table);
      assertThat(chezA).as("lignes de %s visibles par A", table).isPositive();
      assertThat(chezB).as("lignes de %s visibles par B", table).isPositive();
      assertThat(chezA + chezB).as("A + B = tout pour %s", table).isEqualTo(total);
      assertThat(compter(A, "SELECT count(*) FROM " + table + " WHERE tenant_id <> '" + A + "'"))
          .as("lignes étrangères vues par A dans %s", table)
          .isZero();
    }
  }

  @Test
  void sansEcoleSelectionneeAucuneLigneNEstVisible() {
    for (String table : TABLES) {
      assertThat(compter(null, "SELECT count(*) FROM " + table)).as(table).isZero();
    }
  }

  @Test
  void uneEcoleNeModifieNiNeSupprimeLesLignesDUneAutre() {
    assertThat(modifier(B, "UPDATE classe SET nom = 'HACK' WHERE id = ?", classeA)).isZero();
    assertThat(modifier(B, "UPDATE annee_scolaire SET libelle = '2000-2001' WHERE id = ?", anneeA))
        .isZero();
    assertThat(modifier(B, "UPDATE etablissement SET nom = 'HACK' WHERE tenant_id = ?", A))
        .isZero();
    assertThat(modifier(B, "UPDATE niveau SET libelle = 'HACK' WHERE id = ?", niveauA)).isZero();
    assertThat(modifier(B, "UPDATE periode SET libelle = 'HACK' WHERE annee_id = ?", anneeA))
        .isZero();
    // Le rôle applicatif n'a de toute façon pas le droit DELETE.
    assertThatThrownBy(() -> modifier(A, "DELETE FROM classe WHERE id = ?", classeA))
        .isInstanceOf(DataAccessException.class);

    assertThat(
            superUtilisateur()
                .queryForObject("SELECT nom FROM ecole.classe WHERE id = ?", String.class, classeA))
        .isEqualTo("TS2B");
  }

  @Test
  void uneEcoleNePeutPasEcrireUneLigneAuNomDUneAutre() {
    assertThatThrownBy(
            () ->
                modifier(
                    B,
                    "INSERT INTO salle (id, tenant_id, nom, capacite) VALUES (gen_random_uuid(), ?, 'Intruse', 10)",
                    A))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void uneClasseNePeutPasReferencerLeNiveauLAnneeOuLaSalleDUneAutreEcole() {
    UUID anneeDeB = anneeB;
    // Niveau de A, année de B, contexte B : la clé étrangère composite (tenant_id, id) refuse.
    assertThatThrownBy(
            () ->
                modifier(
                    B,
                    "INSERT INTO classe (id, tenant_id, annee_id, niveau_id, nom, capacite)"
                        + " VALUES (gen_random_uuid(), ?, ?, ?, 'CROISEE', 30)",
                    B,
                    anneeDeB,
                    niveauA))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("fk_classe_niveau");

    UUID niveauB =
        superUtilisateur()
            .queryForObject(
                "SELECT id FROM ecole.niveau WHERE tenant_id = ? AND code = 'CP'", UUID.class, B);
    assertThatThrownBy(
            () ->
                modifier(
                    B,
                    "INSERT INTO classe (id, tenant_id, annee_id, niveau_id, nom, capacite)"
                        + " VALUES (gen_random_uuid(), ?, ?, ?, 'CROISEE', 30)",
                    B,
                    anneeA,
                    niveauB))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("fk_classe_annee");

    UUID salleA =
        superUtilisateur()
            .queryForObject("SELECT id FROM ecole.salle WHERE tenant_id = ?", UUID.class, A);
    assertThatThrownBy(
            () ->
                modifier(
                    B,
                    "INSERT INTO classe (id, tenant_id, annee_id, niveau_id, nom, capacite, salle_id)"
                        + " VALUES (gen_random_uuid(), ?, ?, ?, 'CROISEE', 30, ?)",
                    B,
                    anneeDeB,
                    niveauB,
                    salleA))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("fk_classe_salle");
  }

  @Test
  void lesPeriodesEtLesSeriesNeSeMelangentPasEntreEcoles() {
    UUID cycleA =
        superUtilisateur()
            .queryForObject(
                "SELECT id FROM ecole.cycle WHERE tenant_id = ? LIMIT 1", UUID.class, A);
    assertThatThrownBy(
            () ->
                modifier(
                    B,
                    "INSERT INTO periode (id, tenant_id, annee_id, cycle_id, numero, libelle, date_debut, date_fin)"
                        + " VALUES (gen_random_uuid(), ?, ?, ?, 4, 'Croisée', '2026-10-05', '2026-12-01')",
                    B,
                    anneeB,
                    cycleA))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("fk_periode_cycle");

    UUID serieA =
        superUtilisateur()
            .queryForObject(
                "SELECT id FROM ecole.serie WHERE tenant_id = ? LIMIT 1", UUID.class, A);
    UUID niveauB =
        superUtilisateur()
            .queryForObject(
                "SELECT id FROM ecole.niveau WHERE tenant_id = ? LIMIT 1", UUID.class, B);
    assertThatThrownBy(
            () ->
                modifier(
                    B,
                    "INSERT INTO niveau_serie (id, tenant_id, niveau_id, serie_id) VALUES (gen_random_uuid(), ?, ?, ?)",
                    B,
                    niveauB,
                    serieA))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("fk_niveau_serie_serie");
  }
}
