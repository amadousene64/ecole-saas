package sn.ecole;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Toutes les migrations passent sur PostgreSQL 17, et chaque table respecte les règles du projet.
 * Garde-fou pour tous les modules à venir.
 */
class MigrationsTest extends TestIntegration {

  private final JdbcTemplate catalogue = superUtilisateur();

  private List<String> tablesAvecTenant() {
    return catalogue.queryForList(
        """
        SELECT table_name FROM information_schema.columns
        WHERE table_schema = 'ecole' AND column_name = 'tenant_id'""",
        String.class);
  }

  private List<String> tablesMetier() {
    return catalogue.queryForList(
        """
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'ecole' AND table_type = 'BASE TABLE'
          AND table_name <> 'flyway_schema_history'""",
        String.class);
  }

  @Test
  void lesMigrationsSontAppliqueesSansErreur() {
    List<Map<String, Object>> historique =
        catalogue.queryForList("SELECT version, success FROM ecole.flyway_schema_history");
    assertThat(historique)
        .isNotEmpty()
        .allSatisfy(l -> assertThat(l.get("success")).isEqualTo(true));
    assertThat(tablesMetier()).contains("tenant", "appartenance", "outbox_event");
  }

  @Test
  void chaqueTableAvecTenantAUneRlsActiveEtForcee() {
    assertThat(tablesAvecTenant()).isNotEmpty();
    for (String table : tablesAvecTenant()) {
      Map<String, Object> rls =
          catalogue.queryForMap(
              """
              SELECT c.relrowsecurity AS active, c.relforcerowsecurity AS forcee
              FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'ecole' AND c.relname = ?""",
              table);
      assertThat(rls)
          .as("RLS de %s", table)
          .containsEntry("active", true)
          .containsEntry("forcee", true);
    }
  }

  @Test
  void chaqueTableAvecTenantAUnePolitiqueQuiUtiliseNullif() {
    for (String table : tablesAvecTenant()) {
      List<Map<String, Object>> politiques =
          catalogue.queryForList(
              "SELECT qual, with_check FROM pg_policies WHERE schemaname = 'ecole' AND tablename = ?",
              table);
      assertThat(politiques).as("politique RLS de %s", table).isNotEmpty();
      assertThat(politiques)
          .allSatisfy(
              p -> {
                assertThat((String) p.get("qual")).contains("NULLIF").contains("app.tenant_id");
                assertThat((String) p.get("with_check")).contains("NULLIF");
              });
    }
  }

  @Test
  void chaqueTableAvecTenantAUnIndexQuiCommenceParTenantId() {
    for (String table : tablesAvecTenant()) {
      Integer index =
          catalogue.queryForObject(
              """
              SELECT count(*) FROM pg_index i
              JOIN pg_class t ON t.oid = i.indrelid
              JOIN pg_namespace n ON n.oid = t.relnamespace
              JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = i.indkey[0]
              WHERE n.nspname = 'ecole' AND t.relname = ? AND a.attname = 'tenant_id'""",
              Integer.class,
              table);
      assertThat(index).as("index (tenant_id, ...) sur %s", table).isPositive();
    }
  }

  @Test
  void chaqueTableAUnIdUuidLesColonnesDAuditEtUneVersion() {
    for (String table : tablesMetier()) {
      List<String> colonnes =
          catalogue.queryForList(
              """
              SELECT column_name FROM information_schema.columns
              WHERE table_schema = 'ecole' AND table_name = ?""",
              String.class,
              table);
      assertThat(colonnes)
          .as("colonnes de %s", table)
          .contains(
              "id", "cree_le", "cree_par", "modifie_le", "modifie_par", "supprime_le", "version");
      String typeId =
          catalogue.queryForObject(
              """
              SELECT data_type FROM information_schema.columns
              WHERE table_schema = 'ecole' AND table_name = ? AND column_name = 'id'""",
              String.class,
              table);
      assertThat(typeId).as("type de %s.id", table).isEqualTo("uuid");
    }
  }

  @Test
  void leRoleApplicatifNePeutPasContournerLaRls() {
    Map<String, Object> role =
        catalogue.queryForMap(
            "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'ecole_app'");
    assertThat(role).containsEntry("rolsuper", false).containsEntry("rolbypassrls", false);
    Integer tablesPossedees =
        catalogue.queryForObject(
            """
            SELECT count(*) FROM pg_tables
            WHERE schemaname = 'ecole' AND tableowner = 'ecole_app'""",
            Integer.class);
    assertThat(tablesPossedees).isZero();
  }
}
