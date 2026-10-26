package sn.ecole;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base des tests d'intégration : PostgreSQL 17 et Redis réels (Testcontainers), partagés par tous
 * les tests. L'application se connecte avec {@code ecole_app} (soumis à la RLS), Flyway avec {@code
 * ecole_owner}, exactement comme en production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class TestIntegration {

  protected static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.11")
          .withDatabaseName("ecole")
          .withInitScript("db/init-test.sql");

  protected static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.10.2").withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void proprietes(DynamicPropertyRegistry registre) {
    registre.add("spring.datasource.url", TestIntegration::urlEcole);
    registre.add("spring.datasource.username", () -> "ecole_app");
    registre.add("spring.datasource.password", () -> "app-test");
    registre.add("spring.flyway.user", () -> "ecole_owner");
    registre.add("spring.flyway.password", () -> "owner-test");
    registre.add("spring.data.redis.host", REDIS::getHost);
    registre.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired protected StringRedisTemplate redis;

  @BeforeEach
  void installerDonnees() {
    Fixtures.installer(superUtilisateur());
    redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
  }

  protected static String urlEcole() {
    String url = POSTGRES.getJdbcUrl();
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=ecole&logServerErrorDetail=false";
  }

  /** Superutilisateur PostgreSQL : contourne la RLS. Réservé à la préparation des données. */
  protected static JdbcTemplate superUtilisateur() {
    return new JdbcTemplate(
        new DriverManagerDataSource(urlEcole(), POSTGRES.getUsername(), POSTGRES.getPassword()));
  }

  /** Connexion directe avec le rôle applicatif, hors Spring. */
  protected static JdbcTemplate roleApplicatif() {
    return new JdbcTemplate(new DriverManagerDataSource(urlEcole(), "ecole_app", "app-test"));
  }
}
