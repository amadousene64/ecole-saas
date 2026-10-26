package sn.ecole.shared.infrastructure;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalLong;
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
 * Cache Redis des rôles, en texte simple (pas de désérialisation Java).
 *
 * <ul>
 *   <li>{@code ecole:droits-gen:<utilisateur>} : génération courante (compteur, sans expiration ;
 *       en production Redis doit utiliser une politique d'éviction {@code noeviction} ou {@code
 *       volatile-*} pour ne jamais l'évincer).
 *   <li>{@code ecole:roles:<école>:<utilisateur>:<génération>} : rôles, avec expiration.
 * </ul>
 *
 * Une valeur illisible ou une panne Redis en lecture = absence de cache : on relit la base.
 */
@Component
class CacheRolesRedis implements CacheRoles {

  private static final Logger LOG = LoggerFactory.getLogger(CacheRolesRedis.class);

  private final StringRedisTemplate redis;
  private final Duration duree;

  CacheRolesRedis(
      StringRedisTemplate redis, @Value("${ecole.securite.cache-roles-ttl:5m}") Duration duree) {
    this.redis = redis;
    this.duree = duree;
  }

  /**
   * Jamais de génération implicite à 0 : si le compteur est absent (première fois, ou évincé par
   * Redis), il est créé à l'horodatage courant en millisecondes, toujours supérieur aux générations
   * déjà utilisées. Une ancienne entrée encore vivante ne peut donc pas redevenir lisible.
   */
  @Override
  public OptionalLong generation(UUID utilisateurId) {
    try {
      String cle = cleGeneration(utilisateurId);
      String valeur = redis.opsForValue().get(cle);
      if (valeur == null) {
        redis.opsForValue().setIfAbsent(cle, Long.toString(System.currentTimeMillis()));
        valeur = redis.opsForValue().get(cle);
      }
      return valeur == null ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(valeur));
    } catch (DataAccessException | NumberFormatException e) {
      LOG.warn("Génération des droits illisible, cache ignoré : {}", e.getMessage());
      return OptionalLong.empty();
    }
  }

  @Override
  public Optional<Set<RoleEcole>> lire(UUID tenantId, UUID utilisateurId, long generation) {
    String valeur;
    try {
      valeur = redis.opsForValue().get(cle(tenantId, utilisateurId, generation));
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
  public void ecrire(UUID tenantId, UUID utilisateurId, long generation, Set<RoleEcole> roles) {
    String valeur = roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    try {
      redis.opsForValue().set(cle(tenantId, utilisateurId, generation), valeur, duree);
    } catch (DataAccessException e) {
      LOG.warn("Écriture du cache des rôles impossible : {}", e.getMessage());
    }
  }

  @Override
  public void nouvelleGeneration(UUID utilisateurId) {
    redis.opsForValue().increment(cleGeneration(utilisateurId));
  }

  static String cleGeneration(UUID utilisateurId) {
    return "ecole:droits-gen:" + utilisateurId;
  }

  static String cle(UUID tenantId, UUID utilisateurId, long generation) {
    return "ecole:roles:" + tenantId + ":" + utilisateurId + ":" + generation;
  }
}
