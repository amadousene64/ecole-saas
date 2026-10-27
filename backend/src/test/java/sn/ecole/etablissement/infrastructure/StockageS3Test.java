package sn.ecole.etablissement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Le vrai MinIO (même image que infra/docker-compose.yml) : dépôt, URL signée, expiration,
 * falsification, suppression. Les tests d'API, eux, utilisent un stockage en mémoire.
 */
class StockageS3Test {

  private static final String CLE_ACCES = "ecole";
  private static final String CLE_SECRETE = "minio-test-123";

  private static GenericContainer<?> minio;
  private static StockageS3 stockage;
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @BeforeAll
  static void demarrer() {
    minio =
        new GenericContainer<>("bitnamilegacy/minio:2025.7.23-debian-12-r5")
            .withEnv("MINIO_ROOT_USER", CLE_ACCES)
            .withEnv("MINIO_ROOT_PASSWORD", CLE_SECRETE)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));
    minio.start();
    URI endpoint = URI.create("http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
    stockage =
        new StockageS3(
            new StockageProperties(
                endpoint, null, "us-east-1", "test-fichiers", CLE_ACCES, CLE_SECRETE, true));
  }

  @AfterAll
  static void arreter() {
    if (minio != null) {
      minio.stop();
    }
  }

  private static HttpResponse<byte[]> lire(URI url) throws Exception {
    return HTTP.send(
        HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  @Test
  void unFichierDeposeSeLitParUneUrlSignee() throws Exception {
    byte[] contenu = "contenu-image".getBytes(StandardCharsets.UTF_8);
    stockage.deposer("ecole-a/etablissement/logo/un.png", contenu, "image/png");

    HttpResponse<byte[]> reponse =
        lire(stockage.urlSignee("ecole-a/etablissement/logo/un.png", Duration.ofMinutes(5)));

    assertThat(reponse.statusCode()).isEqualTo(200);
    assertThat(reponse.body()).isEqualTo(contenu);
    assertThat(reponse.headers().firstValue("Content-Type")).hasValue("image/png");
  }

  @Test
  void sansSignatureLeBucketEstPrive() throws Exception {
    stockage.deposer("ecole-a/prive.png", new byte[] {1, 2, 3}, "image/png");
    URI signee = stockage.urlSignee("ecole-a/prive.png", Duration.ofMinutes(5));
    URI nue = URI.create(signee.getScheme() + "://" + signee.getAuthority() + signee.getPath());

    assertThat(lire(nue).statusCode()).isEqualTo(403);
  }

  @Test
  void uneUrlSigneePourUnFichierNeDonnePasAccesAUnAutre() throws Exception {
    stockage.deposer("ecole-a/mien.png", new byte[] {1}, "image/png");
    stockage.deposer("ecole-b/autre.png", new byte[] {2}, "image/png");
    URI signee = stockage.urlSignee("ecole-a/mien.png", Duration.ofMinutes(5));
    URI detournee = URI.create(signee.toString().replace("ecole-a/mien.png", "ecole-b/autre.png"));

    assertThat(lire(detournee).statusCode()).isEqualTo(403);
  }

  @Test
  void uneUrlSigneeExpire() throws Exception {
    stockage.deposer("ecole-a/court.png", new byte[] {1}, "image/png");
    URI signee = stockage.urlSignee("ecole-a/court.png", Duration.ofSeconds(1));
    assertThat(lire(signee).statusCode()).isEqualTo(200);

    Thread.sleep(2500);

    assertThat(lire(signee).statusCode()).isEqualTo(403);
  }

  @Test
  void laSuppressionRetireLeFichier() throws Exception {
    stockage.deposer("ecole-a/a-supprimer.png", new byte[] {9}, "image/png");
    URI signee = stockage.urlSignee("ecole-a/a-supprimer.png", Duration.ofMinutes(5));
    assertThat(lire(signee).statusCode()).isEqualTo(200);

    stockage.supprimer("ecole-a/a-supprimer.png");

    assertThat(lire(signee).statusCode()).isEqualTo(404);
  }

  @Test
  void sansClesDAccesLeStockageRefuseDeTravailler() {
    StockageS3 sansCles =
        new StockageS3(
            new StockageProperties(
                URI.create("http://localhost:1"), null, "us-east-1", "b", "", "", false));
    assertThatThrownBy(() -> sansCles.urlSignee("x", Duration.ofMinutes(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non configuré");
  }
}
