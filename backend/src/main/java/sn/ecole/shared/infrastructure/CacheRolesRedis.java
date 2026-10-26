package sn.ecole.shared.infrastructure;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import sn.ecole.shared.domain.CacheRoles;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Cache Redis des rôles par (école, utilisateur), en texte simple (pas de désérialisation Java).
 * Une valeur illisible ou une panne Redis = absence de cache : on relit la base.
 */
@Component
class CacheRolesRedis implements CacheRoles {

  private static final Logger LOG = LoggerFactory.getLogger(CacheRolesRedis.class);
  private static final String PREFIXE = "ecole:roles:";

  private final StringRedisTemplate redis;
  private final Duration duree;

  CacheRolesRedis(
      StringRedisTemplate redis, @Value("${ecole.securite.cache-roles-ttl:5m}") Duration duree) {
    this.redis = redis;
    this.duree = duree;
  }

  @Override
  public Optional<Set<RoleEcole>> lire(UUID tenantId, UUID utilisateurId) {
    String valeur;
    try {
      valeur = redis.opsForValue().get(cle(tenantId, utilisateurId));
    } catch (DataAccessException e) {
      LOG.warn("Cache des rôles indisponible, lecture en base : {}", e.getMessage());
      return Optional.empty();
    }
    if (valeur == null) {
      return Optional.empty();
    }
    try {
      Set<RoleEcole> roles = EnumSet.noneOf(RoleEcole.class);
      Arrays.stream(valeur.split(","))
          .filter(s -> !s.isBlank())
          .forEach(s -> roles.add(RoleEcole.valueOf(s)));
      return Optional.of(roles);
    } catch (IllegalArgumentException e) {
      LOG.warn("Valeur de cache des rôles illisible, ignorée");
      return Optional.empty();
    }
  }

  @Override
  public void ecrire(UUID tenantId, UUID utilisateurId, Set<RoleEcole> roles) {
    String valeur = roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    try {
      redis.opsForValue().set(cle(tenantId, utilisateurId), valeur, duree);
    } catch (DataAccessException e) {
      LOG.warn("Écriture du cache des rôles impossible : {}", e.getMessage());
    }
  }

  @Override
  public void invalider(UUID tenantId, UUID utilisateurId) {
    try {
      redis.delete(cle(tenantId, utilisateurId));
    } catch (DataAccessException e) {
      // Droits potentiellement périmés jusqu'à expiration (ecole.securite.cache-roles-ttl).
      LOG.error(
          "Invalidation du cache des rôles impossible pour l'école {} : droits périmés possibles"
              + " pendant {}",
          tenantId,
          duree,
          e);
    }
  }

  static String cle(UUID tenantId, UUID utilisateurId) {
    return PREFIXE + tenantId + ":" + utilisateurId;
  }
}
