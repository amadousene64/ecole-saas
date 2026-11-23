package sn.ecole.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import sn.ecole.TestIntegration;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Retirer un rôle ne dépend jamais de Redis : la base fait foi, l'invalidation ratée est rejouée
 * par l'outbox, et le cache est ignoré tant qu'elle attend.
 */
class RetraitRoleSansRedisTest extends TestIntegration {

  @Autowired AppartenanceService service;
  @Autowired ExecutionPourTenant execution;
  @Autowired RelaisOutbox relais;

  private final UUID cible = UUID.randomUUID();

  @BeforeEach
  void connecterLeDirecteurDeA() {
    Jwt jwt =
        Jwt.withTokenValue("essai")
            .header("alg", "none")
            .subject(DIRECTEUR_A.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTEUR")),
                DIRECTEUR_A.toString()));
  }

  @AfterEach
  void deconnecter() {
    SecurityContextHolder.clearContext();
  }

  private int invalidationsEnAttente() {
    return superUtilisateur()
        .queryForObject(
            "SELECT count(*) FROM ecole.outbox_event WHERE type_evenement = ?"
                + " AND id_agregat = ? AND traite_le IS NULL",
            Integer.class,
            InvalidationDroits.A_INVALIDER,
            cible);
  }

  @Test
  void retirerUnRoleReussitRedisArreteEtLeRoleEstRetire() {
    execution.executer(ECOLE_A, () -> service.attribuer(cible, RoleEcole.SURVEILLANT));
    relais.traiterTout();
    // Rôles lus et mis en cache avant la panne.
    assertThat(service.rolesPour(ECOLE_A, cible)).containsExactly(RoleEcole.SURVEILLANT);
    assertThat(service.rolesPour(ECOLE_A, cible)).containsExactly(RoleEcole.SURVEILLANT);

    var docker = REDIS.getDockerClient();
    docker.pauseContainerCmd(REDIS.getContainerId()).exec();
    try {
      execution.executer(ECOLE_A, () -> service.retirer(cible, RoleEcole.SURVEILLANT));
      // Redis arrêté : lecture en base, le rôle est déjà retiré.
      assertThat(service.rolesPour(ECOLE_A, cible)).isEmpty();
      assertThat(invalidationsEnAttente()).isEqualTo(1);
    } finally {
      docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
    }
    // Redis revenu, invalidation pas encore rejouée : l'entrée d'avant la panne n'est pas lue.
    assertThat(service.rolesPour(ECOLE_A, cible)).isEmpty();
    // L'outbox rejoue l'invalidation ; le cache sert de nouveau, avec l'état de la base.
    relais.traiterTout();
    assertThat(invalidationsEnAttente()).isZero();
    assertThat(service.rolesPour(ECOLE_A, cible)).isEmpty();
    assertThat(service.rolesPour(ECOLE_A, cible)).isEmpty();
  }
}
