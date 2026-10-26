package sn.ecole.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import sn.ecole.shared.domain.RoleEcole;

/** Redis injoignable : le cache se comporte comme vide, sans faire échouer la requête. */
class CacheRolesRedisPanneTest {

  private final LettuceConnectionFactory connexions =
      new LettuceConnectionFactory(
          new RedisStandaloneConfiguration("127.0.0.1", 1),
          LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build());

  private final CacheRolesRedis cache;

  CacheRolesRedisPanneTest() {
    connexions.afterPropertiesSet();
    connexions.start();
    cache = new CacheRolesRedis(new StringRedisTemplate(connexions), Duration.ofMinutes(5));
  }

  @AfterEach
  void fermer() {
    connexions.destroy();
  }

  @Test
  void lectureEcritureEtInvalidationNeLevenPasDErreur() {
    UUID ecole = UUID.randomUUID();
    UUID utilisateur = UUID.randomUUID();
    assertThat(cache.lire(ecole, utilisateur)).isEmpty();
    assertThatCode(() -> cache.ecrire(ecole, utilisateur, Set.of(RoleEcole.PARENT)))
        .doesNotThrowAnyException();
    assertThatCode(() -> cache.invalider(ecole, utilisateur)).doesNotThrowAnyException();
  }
}
