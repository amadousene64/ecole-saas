package sn.ecole.personnel.infrastructure;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.personnel.domain.CachePerimetre;
import sn.ecole.personnel.domain.CachePerimetre.CycleSurveille;

/**
 * Cache Redis du périmètre (vie courte, 2 min par défaut), en texte simple : {@code
 * ecole:perimetre:<école>:<utilisateur>: <génération>:<jour>} → {@code classes|cycles}
 * (identifiants et codes séparés par des virgules), avec expiration. Une valeur illisible ou une
 * panne Redis = absence de cache.
 */
@Component
class CachePerimetreRedis implements CachePerimetre {

  private static final Logger LOG = LoggerFactory.getLogger(CachePerimetreRedis.class);

  private final StringRedisTemplate redis;
  private final Duration duree;

  CachePerimetreRedis(
      StringRedisTemplate redis,
      @Value("${ecole.securite.cache-perimetre-ttl:2m}") Duration duree) {
    this.redis = redis;
    this.duree = duree;
  }

  @Override
  public Optional<Calcule> lire(
      UUID tenantId, UUID utilisateurId, long generation, LocalDate jour) {
    String valeur;
    try {
      valeur = redis.opsForValue().get(cle(tenantId, utilisateurId, generation, jour));
    } catch (DataAccessException e) {
      LOG.warn("Cache du périmètre indisponible, lecture en base : {}", e.getMessage());
      return Optional.empty();
    }
    if (valeur == null) {
      return Optional.empty();
    }
    try {
      String[] parties = valeur.split("\\|", -1);
      Set<UUID> classes = new HashSet<>();
      for (String id : parties[0].split(",")) {
        if (!id.isEmpty()) {
          classes.add(UUID.fromString(id));
        }
      }
      Set<CycleSurveille> cycles = new HashSet<>();
      for (String paire : parties[1].split(",")) {
        if (!paire.isEmpty()) {
          String[] ac = paire.split(":", 2);
          cycles.add(new CycleSurveille(UUID.fromString(ac[0]), CodeCycle.valueOf(ac[1])));
        }
      }
      return Optional.of(new Calcule(classes, cycles));
    } catch (RuntimeException e) {
      LOG.warn("Périmètre en cache illisible, ignoré");
      return Optional.empty();
    }
  }

  @Override
  public void ecrire(
      UUID tenantId, UUID utilisateurId, long generation, LocalDate jour, Calcule calcule) {
    String valeur =
        String.join(",", calcule.classes().stream().map(UUID::toString).toList())
            + "|"
            + String.join(
                ",",
                calcule.cycles().stream().map(c -> c.anneeId() + ":" + c.codeCycle()).toList());
    try {
      redis.opsForValue().set(cle(tenantId, utilisateurId, generation, jour), valeur, duree);
    } catch (DataAccessException e) {
      LOG.warn("Cache du périmètre indisponible en écriture : {}", e.getMessage());
    }
  }

  private static String cle(UUID tenantId, UUID utilisateurId, long generation, LocalDate jour) {
    return "ecole:perimetre:" + tenantId + ":" + utilisateurId + ":" + generation + ":" + jour;
  }
}
