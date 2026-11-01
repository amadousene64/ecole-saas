package sn.ecole.statistique.infrastructure;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.statistique.domain.CacheTableauDeBord;
import sn.ecole.statistique.domain.PeriodeCourbe;
import sn.ecole.statistique.domain.TableauDeBord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache Redis du tableau de bord en JSON, clé {@code ecole:stats:direction:<école>:<année>:
 * <période>:<cycle>} et expiration courte (5 minutes par défaut). La clé porte toujours l'école :
 * deux écoles ne partagent jamais une entrée. Une panne Redis ou une valeur illisible = absence de
 * cache : on recalcule depuis la base, sans erreur pour le directeur.
 */
@Component
class CacheTableauDeBordRedis implements CacheTableauDeBord {

  private static final Logger LOG = LoggerFactory.getLogger(CacheTableauDeBordRedis.class);

  private final StringRedisTemplate redis;
  private final Duration duree;
  private final JsonMapper json = JsonMapper.builder().build();

  CacheTableauDeBordRedis(
      StringRedisTemplate redis, @Value("${ecole.statistique.cache-ttl:5m}") Duration duree) {
    this.redis = redis;
    this.duree = duree;
  }

  @Override
  public Optional<TableauDeBord> lire(
      UUID tenantId, UUID anneeId, PeriodeCourbe periode, CodeCycle cycle) {
    try {
      String valeur = redis.opsForValue().get(cle(tenantId, anneeId, periode, cycle));
      if (valeur == null) {
        return Optional.empty();
      }
      return Optional.of(json.readValue(valeur, TableauDeBord.class));
    } catch (DataAccessException | JacksonException e) {
      LOG.warn("Cache des statistiques ignoré : {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  @Override
  public void ecrire(
      UUID tenantId, UUID anneeId, PeriodeCourbe periode, CodeCycle cycle, TableauDeBord tableau) {
    try {
      redis
          .opsForValue()
          .set(cle(tenantId, anneeId, periode, cycle), json.writeValueAsString(tableau), duree);
    } catch (DataAccessException | JacksonException e) {
      LOG.warn("Statistiques non mises en cache : {}", e.getClass().getSimpleName());
    }
  }

  static String cle(UUID tenantId, UUID anneeId, PeriodeCourbe periode, CodeCycle cycle) {
    return "ecole:stats:direction:"
        + tenantId
        + ":"
        + anneeId
        + ":"
        + periode
        + ":"
        + (cycle == null ? "TOUS" : cycle.name());
  }
}
