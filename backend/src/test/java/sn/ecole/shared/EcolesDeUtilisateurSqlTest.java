package sn.ecole.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.SURVEILLANT_B;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import sn.ecole.TestIntegration;

/**
 * La fonction {@code ecoles_de_utilisateur} est la SEULE façon, pour {@code ecole_app}, de lire des
 * appartenances hors de l'école courante.
 */
class EcolesDeUtilisateurSqlTest extends TestIntegration {

  private static final String FONCTION = "ecole.ecoles_de_utilisateur(uuid)";

  /** Exécute dans une transaction du rôle applicatif dont l'école courante vaut {@code ecole}. */
  private static <T> T avecEcole(UUID ecole, ConnectionCallback<T> action) {
    return roleApplicatif()
        .execute(
            (ConnectionCallback<T>)
                connexion -> {
                  connexion.setAutoCommit(false);
                  try (PreparedStatement ps =
                      connexion.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, ecole == null ? "" : ecole.toString());
                    ps.execute();
                  }
                  try {
                    return action.doInConnection(connexion);
                  } finally {
                    connexion.rollback();
                  }
                });
  }

  /** Première colonne de chaque ligne, lue avec le rôle applicatif et l'école donnée. */
  private static List<String> lister(UUID ecole, String sql, Object... params) {
    ConnectionCallback<List<String>> requete =
        c -> {
          try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
              ps.setObject(i + 1, params[i]);
            }
            List<String> valeurs = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                valeurs.add(rs.getString(1));
              }
            }
            return valeurs;
          }
        };
    return avecEcole(ecole, requete);
  }

  private static int modifier(UUID ecole, String sql, Object... params) {
    ConnectionCallback<Integer> requete =
        c -> {
          try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
              ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
          }
        };
    return avecEcole(ecole, requete);
  }

  @Test
  void sansEcoleCouranteLeRoleApplicatifNeLitAucuneAppartenance() {
    assertThat(lister(null, "select utilisateur_id::text from appartenance")).isEmpty();
  }

  @Test
  void avecUneEcoleCouranteLeRoleApplicatifNeLitQueCetteEcole() {
    assertThat(lister(ECOLE_A, "select distinct tenant_id::text from appartenance"))
        .containsExactly(ECOLE_A.toString());
    // Les lignes de B existent mais restent invisibles depuis A, même en les visant par id.
    assertThat(
            lister(
                ECOLE_A,
                "select tenant_id::text from appartenance where utilisateur_id = ?",
                SURVEILLANT_B))
        .isEmpty();
  }

  @Test
  void lAppartenanceNEstLisibleHorsEcoleQueParLaFonction() {
    String sql = "select ecole_id::text from ecoles_de_utilisateur(?)";
    assertThat(lister(ECOLE_A, sql, SURVEILLANT_B)).containsExactly(ECOLE_B.toString());
    assertThat(lister(null, sql, ENSEIGNANT_A_PARENT_B))
        .containsExactlyInAnyOrder(ECOLE_A.toString(), ECOLE_B.toString());
  }

  @Test
  void laFonctionNeRenvoieQueIdCodeEtNom() {
    ConnectionCallback<List<String>> requete =
        c -> {
          try (PreparedStatement ps =
              c.prepareStatement("select * from ecoles_de_utilisateur(?)")) {
            ps.setObject(1, SURVEILLANT_B);
            try (ResultSet rs = ps.executeQuery()) {
              List<String> noms = new ArrayList<>();
              for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                noms.add(rs.getMetaData().getColumnName(i));
              }
              return noms;
            }
          }
        };
    assertThat(avecEcole(null, requete)).containsExactly("ecole_id", "ecole_code", "ecole_nom");
  }

  @Test
  void laFonctionEstDefinerDuProprietaireAvecUnSearchPathFige() {
    JdbcTemplate su = superUtilisateur();
    assertThat(
            su.queryForObject(
                "select prosecdef from pg_proc where oid = ?::regprocedure",
                Boolean.class,
                FONCTION))
        .isTrue();
    assertThat(
            su.queryForObject(
                "select pg_get_userbyid(proowner) from pg_proc where oid = ?::regprocedure",
                String.class,
                FONCTION))
        .isEqualTo("ecole_owner");
    assertThat(
            su.queryForObject(
                "select array_to_string(proconfig, ',') from pg_proc where oid = ?::regprocedure",
                String.class,
                FONCTION))
        .isEqualTo("search_path=pg_catalog, pg_temp");
  }

  @Test
  void seulsLeProprietaireEtEcoleAppPeuventExecuterLaFonction() {
    List<String> beneficiaires =
        superUtilisateur()
            .queryForList(
                """
                select distinct case when e.grantee = 0 then 'PUBLIC' else pg_get_userbyid(e.grantee) end
                from pg_proc p, aclexplode(p.proacl) e
                where p.oid = ?::regprocedure and e.privilege_type = 'EXECUTE'
                order by 1""",
                String.class,
                FONCTION);
    assertThat(beneficiaires).containsExactly("ecole_app", "ecole_owner");
  }

  @Test
  void unAutreRoleNePeutPasExecuterLaFonction() {
    JdbcTemplate su = superUtilisateur();
    su.execute("CREATE ROLE intrus_essai LOGIN PASSWORD 'intrus' NOSUPERUSER NOBYPASSRLS");
    try {
      su.execute("GRANT USAGE ON SCHEMA ecole TO intrus_essai");
      JdbcTemplate intrus =
          new JdbcTemplate(new DriverManagerDataSource(urlEcole(), "intrus_essai", "intrus"));
      assertThatThrownBy(
              () ->
                  intrus.queryForList(
                      "select * from ecoles_de_utilisateur(?::uuid)", SURVEILLANT_B.toString()))
          .isInstanceOf(DataAccessException.class)
          .rootCause()
          .hasMessageContaining("permission denied");
    } finally {
      su.execute("DROP OWNED BY intrus_essai");
      su.execute("DROP ROLE intrus_essai");
    }
  }

  @Test
  void leRoleApplicatifNePeutPasModifierLesAppartenancesDUneAutreEcole() {
    assertThat(
            modifier(
                ECOLE_A,
                "update appartenance set supprime_le = now() where tenant_id = ?",
                ECOLE_B))
        .isZero();
  }
}
