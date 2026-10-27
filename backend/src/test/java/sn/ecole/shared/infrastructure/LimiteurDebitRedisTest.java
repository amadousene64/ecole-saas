package sn.ecole.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.ecole.TestIntegration;
import sn.ecole.shared.domain.LimiteurDebit;

class LimiteurDebitRedisTest extends TestIntegration {

  @Autowired LimiteurDebit limiteur;

  @Test
  void auDelaDuPlafondLesTentativesSontRefuseesPuisLaFenetreExpire() throws Exception {
    for (int i = 0; i < 3; i++) {
      assertThat(limiteur.autoriser("essai:ip", 3, Duration.ofMillis(800))).isTrue();
    }
    assertThat(limiteur.autoriser("essai:ip", 3, Duration.ofMillis(800))).isFalse();
    assertThat(limiteur.autoriser("essai:autre", 3, Duration.ofMillis(800))).isTrue();

    Long ttl = redis.getExpire("limite:essai:ip");
    assertThat(ttl).isNotNull().isNotEqualTo(-1L); // jamais de compteur sans expiration

    Thread.sleep(1200);
    assertThat(limiteur.autoriser("essai:ip", 3, Duration.ofMillis(800))).isTrue();
  }
}
