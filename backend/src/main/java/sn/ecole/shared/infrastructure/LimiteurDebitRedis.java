package sn.ecole.shared.infrastructure;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import sn.ecole.shared.domain.LimiteurDebit;

/** INCR + EXPIRE sur {@code limite:<cle>} ; la clé ne doit contenir aucune donnée personnelle. */
@Component
class LimiteurDebitRedis implements LimiteurDebit {

  private static final Logger LOG = LoggerFactory.getLogger(LimiteurDebitRedis.class);

  private static final RedisScript<Long> SCRIPT =
      RedisScript.of(
          """
          local c = redis.call('INCR', KEYS[1])
          if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
          return c""",
          Long.class);

  private final StringRedisTemplate redis;

  LimiteurDebitRedis(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean autoriser(String cle, int max, Duration fenetre) {
    String cleRedis = "limite:" + cle;
    try {
      // Script atomique : le compteur naît avec son expiration, jamais de compteur éternel.
      Long compte = redis.execute(SCRIPT, List.of(cleRedis), String.valueOf(fenetre.toMillis()));
      return compte != null && compte <= max;
    } catch (RuntimeException e) {
      LOG.warn(
          "Limiteur de débit indisponible : tentative refusée ({})", e.getClass().getSimpleName());
      return false;
    }
  }
}
