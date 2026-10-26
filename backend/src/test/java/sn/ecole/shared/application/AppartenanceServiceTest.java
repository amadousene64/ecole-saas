package sn.ecole.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;
import static sn.ecole.Fixtures.ECOLE_INACTIVE;
import static sn.ecole.Fixtures.ENSEIGNANT_A_PARENT_B;
import static sn.ecole.Fixtures.MEMBRE_INACTIVE;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import sn.ecole.Fixtures;
import sn.ecole.TestIntegration;
import sn.ecole.shared.domain.CacheRoles;
import sn.ecole.shared.domain.RoleEcole;

class AppartenanceServiceTest extends TestIntegration {

  @Autowired AppartenanceService service;
  @Autowired ExecutionPourTenant execution;

  private static void connecte(UUID utilisateur, RoleEcole... roles) {
    Jwt jwt =
        Jwt.withTokenValue("essai")
            .header("alg", "none")
            .subject(utilisateur.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    var autorites =
        Arrays.stream(roles).map(r -> new SimpleGrantedAuthority(r.autorite())).toList();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, autorites, utilisateur.toString()));
  }

  @Autowired CacheRoles cache;

  /** Clé du cache pour la génération courante de l'utilisateur. */
  private String cle(UUID ecole, UUID utilisateur) {
    return "ecole:roles:"
        + ecole
        + ":"
        + utilisateur
        + ":"
        + cache.generation(utilisateur).getAsLong();
  }

  @AfterEach
  void deconnecter() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void lesRolesSontCeuxDeLEcoleDemandeeUniquement() {
    connecte(ENSEIGNANT_A_PARENT_B);
    assertThat(service.rolesPour(ECOLE_A, ENSEIGNANT_A_PARENT_B))
        .containsExactly(RoleEcole.ENSEIGNANT);
    assertThat(service.rolesPour(ECOLE_B, ENSEIGNANT_A_PARENT_B)).containsExactly(RoleEcole.PARENT);
  }

  @Test
  void uneEcoleInactiveNeDonneAucunRole() {
    connecte(MEMBRE_INACTIVE);
    assertThat(service.rolesPour(ECOLE_INACTIVE, MEMBRE_INACTIVE)).isEmpty();
  }

  @Test
  void lesRolesSontMisEnCacheDansRedis() {
    connecte(ENSEIGNANT_A_PARENT_B);
    service.rolesPour(ECOLE_B, ENSEIGNANT_A_PARENT_B);
    assertThat(redis.opsForValue().get(cle(ECOLE_B, ENSEIGNANT_A_PARENT_B))).isEqualTo("PARENT");
    assertThat(redis.getExpire(cle(ECOLE_B, ENSEIGNANT_A_PARENT_B))).isPositive();

    // Ajout direct en base : le cache sert toujours l'ancienne valeur jusqu'à invalidation.
    Fixtures.appartenance(superUtilisateur(), ECOLE_B, ENSEIGNANT_A_PARENT_B, "COMPTABLE");
    assertThat(service.rolesPour(ECOLE_B, ENSEIGNANT_A_PARENT_B)).containsExactly(RoleEcole.PARENT);

    superUtilisateur()
        .update(
            "DELETE FROM appartenance WHERE tenant_id = ? AND utilisateur_id = ? AND role = 'COMPTABLE'",
            ECOLE_B,
            ENSEIGNANT_A_PARENT_B);
  }

  @Test
  void unNonMembreEstAussiMisEnCacheCommeSansRole() {
    connecte(DIRECTEUR_A);
    assertThat(service.rolesPour(ECOLE_B, DIRECTEUR_A)).isEmpty();
    assertThat(redis.opsForValue().get(cle(ECOLE_B, DIRECTEUR_A))).isEmpty();
    assertThat(service.rolesPour(ECOLE_B, DIRECTEUR_A)).isEmpty();
  }

  @Test
  void uneEcoleInconnueOuInactiveNEstPasMiseEnCache() {
    connecte(DIRECTEUR_A);
    UUID inconnue = UUID.randomUUID();
    assertThat(service.rolesPour(inconnue, DIRECTEUR_A)).isEmpty();
    assertThat(redis.hasKey(cle(inconnue, DIRECTEUR_A))).isFalse();
    assertThat(service.rolesPour(ECOLE_INACTIVE, MEMBRE_INACTIVE)).isEmpty();
    assertThat(redis.hasKey(cle(ECOLE_INACTIVE, MEMBRE_INACTIVE))).isFalse();
  }

  @Test
  void uneValeurDeCacheIllisibleEstIgnoree() {
    connecte(DIRECTEUR_A);
    redis.opsForValue().set(cle(ECOLE_A, DIRECTEUR_A), "SUPER_ADMIN");
    assertThat(service.rolesPour(ECOLE_A, DIRECTEUR_A)).containsExactly(RoleEcole.DIRECTEUR);
  }

  @Test
  void leDirecteurAttribueEtRetireUnRoleEtLeCacheEstInvalide() {
    UUID nouveau = UUID.randomUUID();
    connecte(DIRECTEUR_A, RoleEcole.DIRECTEUR);
    assertThat(service.rolesPour(ECOLE_A, nouveau)).isEmpty(); // mis en cache « aucun rôle »

    String cleAvant = cle(ECOLE_A, nouveau);
    execution.executer(ECOLE_A, () -> service.attribuer(nouveau, RoleEcole.SECRETARIAT));
    assertThat(cle(ECOLE_A, nouveau)).isNotEqualTo(cleAvant); // nouvelle génération
    assertThat(redis.hasKey(cle(ECOLE_A, nouveau))).isFalse();
    assertThat(service.rolesPour(ECOLE_A, nouveau)).containsExactly(RoleEcole.SECRETARIAT);
    assertThat(service.rolesPour(ECOLE_B, nouveau)).isEmpty();

    execution.executer(
        ECOLE_A, () -> service.attribuer(nouveau, RoleEcole.SECRETARIAT)); // idempotent
    execution.executer(ECOLE_A, () -> service.retirer(nouveau, RoleEcole.SECRETARIAT));
    assertThat(service.rolesPour(ECOLE_A, nouveau)).isEmpty();
  }

  @Test
  void unRoleRevoqueCesseImmediatement() {
    UUID revoque = UUID.randomUUID();
    connecte(DIRECTEUR_A, RoleEcole.DIRECTEUR);
    execution.executer(ECOLE_A, () -> service.attribuer(revoque, RoleEcole.COMPTABLE));
    assertThat(service.rolesPour(ECOLE_A, revoque)).containsExactly(RoleEcole.COMPTABLE);
    assertThat(redis.hasKey(cle(ECOLE_A, revoque))).isTrue(); // en cache

    execution.executer(ECOLE_A, () -> service.retirer(revoque, RoleEcole.COMPTABLE));

    assertThat(service.rolesPour(ECOLE_A, revoque)).isEmpty();
  }

  @Test
  void uneLectureConcurrenteQuiEcritLAncienEtatApresLaRevocationEstIgnoree() {
    UUID revoque = UUID.randomUUID();
    connecte(DIRECTEUR_A, RoleEcole.DIRECTEUR);
    execution.executer(ECOLE_A, () -> service.attribuer(revoque, RoleEcole.COMPTABLE));

    // Une requête lente a lu la génération et l'ancien état AVANT la révocation…
    long generationLue = cache.generation(revoque).getAsLong();
    execution.executer(ECOLE_A, () -> service.retirer(revoque, RoleEcole.COMPTABLE));
    // … et écrit son résultat périmé APRÈS la révocation.
    cache.ecrire(ECOLE_A, revoque, generationLue, java.util.Set.of(RoleEcole.COMPTABLE));

    assertThat(service.rolesPour(ECOLE_A, revoque)).isEmpty();
  }

  @Test
  void siRedisEvinceLeCompteurUneAncienneEntreeNeRedevientPasLisible() {
    UUID revoque = UUID.randomUUID();
    connecte(DIRECTEUR_A, RoleEcole.DIRECTEUR);
    execution.executer(ECOLE_A, () -> service.attribuer(revoque, RoleEcole.COMPTABLE));
    long ancienne = cache.generation(revoque).getAsLong();
    assertThat(ancienne).isPositive(); // jamais 0 implicite
    // Entrée périmée encore vivante, puis éviction du compteur et retrait du rôle en base.
    cache.ecrire(ECOLE_A, revoque, ancienne, java.util.Set.of(RoleEcole.COMPTABLE));
    superUtilisateur().update("DELETE FROM appartenance WHERE utilisateur_id = ?", revoque);
    redis.delete("ecole:droits-gen:" + revoque);

    assertThat(cache.generation(revoque).getAsLong()).isGreaterThan(ancienne);
    assertThat(service.rolesPour(ECOLE_A, revoque)).isEmpty();
  }

  @Test
  void seulUnDirecteurPeutAttribuerUnRole() {
    connecte(ENSEIGNANT_A_PARENT_B, RoleEcole.ENSEIGNANT);
    assertThatThrownBy(
            () ->
                execution.executer(
                    ECOLE_A, () -> service.attribuer(ENSEIGNANT_A_PARENT_B, RoleEcole.DIRECTEUR)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                execution.executer(
                    ECOLE_A, () -> service.retirer(DIRECTEUR_A, RoleEcole.DIRECTEUR)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void sansAuthentificationAucunRoleNEstLu() {
    assertThatThrownBy(() -> service.rolesPour(ECOLE_A, DIRECTEUR_A))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }
}
