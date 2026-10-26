package sn.ecole.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Redis injoignable : la lecture et l'écriture se comportent comme un cache vide, mais un
 * changement de génération échoue (l'appelant annule alors le changement de droits).
 */
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
  void lectureEtEcritureSontIgnoreesMaisLeChangementDeGenerationEchoue() {
    UUID ecole = UUID.randomUUID();
    UUID utilisateur = UUID.randomUUID();
    assertThat(cache.generation(utilisateur)).isEmpty();
    assertThat(cache.lire(ecole, utilisateur, 0L)).isEmpty();
    assertThatCode(() -> cache.ecrire(ecole, utilisateur, 0L, Set.of(RoleEcole.PARENT)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> cache.nouvelleGeneration(utilisateur))
        .isInstanceOf(DataAccessException.class);
  }
}
