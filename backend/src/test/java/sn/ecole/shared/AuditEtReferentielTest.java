package sn.ecole.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import sn.ecole.TestIntegration;
import sn.ecole.shared.application.AppartenanceService;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.Appartenance;
import sn.ecole.shared.domain.AppartenanceRepository;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

class AuditEtReferentielTest extends TestIntegration {

  @Autowired AppartenanceService service;
  @Autowired AppartenanceRepository appartenances;
  @Autowired TenantRepository tenants;
  @Autowired ExecutionPourTenant execution;

  @AfterEach
  void deconnecter() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void lesColonnesDAuditSontRenseigneesAvecLeSubDuJeton() {
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
    UUID nouveau = UUID.randomUUID();

    execution.executer(ECOLE_A, () -> service.attribuer(nouveau, RoleEcole.COMPTABLE));

    Appartenance enregistree =
        execution.lire(ECOLE_A, () -> appartenances.findByUtilisateurId(nouveau).getFirst());
    assertThat(enregistree.getTenantId()).isEqualTo(ECOLE_A);
    assertThat(enregistree.getCreePar()).isEqualTo(DIRECTEUR_A);
    assertThat(enregistree.getModifiePar()).isEqualTo(DIRECTEUR_A);
    assertThat(enregistree.getCreeLe()).isNotNull();
    assertThat(enregistree.getModifieLe()).isNotNull();
    assertThat(enregistree.getVersion()).isZero();

    Map<String, Object> ligne =
        superUtilisateur()
            .queryForMap(
                "SELECT tenant_id, cree_par FROM appartenance WHERE utilisateur_id = ?", nouveau);
    assertThat(ligne).containsEntry("tenant_id", ECOLE_A).containsEntry("cree_par", DIRECTEUR_A);
  }

  @Test
  void leReferentielDesEcolesSeLitParSousDomaine() {
    Tenant lycee = tenants.findBySousDomaine("lycee-a").orElseThrow();
    assertThat(lycee.getId()).isEqualTo(ECOLE_A);
    assertThat(lycee.getCode()).isEqualTo("LYCEE-A");
    assertThat(lycee.getNom()).isEqualTo("Lycée Exemple A");
    assertThat(lycee.getSousDomaine()).isEqualTo("lycee-a");
    assertThat(lycee.isActif()).isTrue();
    assertThat(lycee.estSupprime()).isFalse();
    assertThat(tenants.findBySousDomaine("inconnu")).isEmpty();
  }
}
