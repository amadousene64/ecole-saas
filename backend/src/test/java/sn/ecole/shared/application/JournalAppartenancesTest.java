package sn.ecole.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.IllegalTransactionStateException;
import sn.ecole.TestIntegration;
import sn.ecole.shared.domain.EntreeJournalAudit;
import sn.ecole.shared.domain.JournalAuditRepository;
import sn.ecole.shared.domain.RoleEcole;

/** Attribution et retrait de rôles : suppression logique et journal d'audit. */
class JournalAppartenancesTest extends TestIntegration {

  @Autowired AppartenanceService service;
  @Autowired JournalAuditRepository journal;
  @Autowired JournalAudit journalAudit;
  @Autowired ExecutionPourTenant execution;

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

  private List<EntreeJournalAudit> journalDe(UUID ecole, UUID utilisateur) {
    return execution.lire(
        ecole,
        () ->
            journal.findByTypeCibleAndIdCibleOrderByCreeLeAsc(
                AppartenanceService.CIBLE_UTILISATEUR, utilisateur));
  }

  private List<Map<String, Object>> lignesEnBase(UUID utilisateur) {
    return superUtilisateur()
        .queryForList(
            "SELECT role, supprime_le, modifie_par FROM appartenance WHERE utilisateur_id = ?"
                + " ORDER BY cree_le",
            utilisateur);
  }

  @Test
  void leRetraitEstUneSuppressionLogique() {
    execution.executer(ECOLE_A, () -> service.attribuer(cible, RoleEcole.COMPTABLE));
    execution.executer(ECOLE_A, () -> service.retirer(cible, RoleEcole.COMPTABLE));

    List<Map<String, Object>> lignes = lignesEnBase(cible);
    assertThat(lignes).hasSize(1);
    assertThat(lignes.getFirst().get("supprime_le")).isNotNull();
    assertThat(lignes.getFirst()).containsEntry("modifie_par", DIRECTEUR_A);
    assertThat(service.rolesPour(ECOLE_A, cible)).isEmpty();
  }

  @Test
  void attributionEtRetraitSontJournalisesAvecQuiQuandRoleEtEcole() {
    Instant avant = Instant.now().minusSeconds(1);
    execution.executer(ECOLE_A, () -> service.attribuer(cible, RoleEcole.COMPTABLE));
    execution.executer(ECOLE_A, () -> service.retirer(cible, RoleEcole.COMPTABLE));

    List<EntreeJournalAudit> entrees = journalDe(ECOLE_A, cible);
    assertThat(entrees)
        .extracting(EntreeJournalAudit::getAction)
        .containsExactly(AppartenanceService.ROLE_ATTRIBUE, AppartenanceService.ROLE_RETIRE);
    assertThat(entrees)
        .allSatisfy(
            e -> {
              assertThat(e.getActeurId()).isEqualTo(DIRECTEUR_A); // qui
              assertThat(e.getCreeLe()).isAfter(avant); // quand
              assertThat(e.getDetails()).contains("\"role\"").contains("COMPTABLE"); // rôle
              assertThat(e.getTenantId()).isEqualTo(ECOLE_A); // école
              assertThat(e.getIdCible()).isEqualTo(cible);
            });
    // Le journal de A n'est pas visible depuis B.
    assertThat(journalDe(ECOLE_B, cible)).isEmpty();
  }

  @Test
  void unRoleRetirePeutEtreRedonne() {
    execution.executer(ECOLE_A, () -> service.attribuer(cible, RoleEcole.PARENT));
    execution.executer(ECOLE_A, () -> service.retirer(cible, RoleEcole.PARENT));
    execution.executer(ECOLE_A, () -> service.attribuer(cible, RoleEcole.PARENT));

    assertThat(lignesEnBase(cible)).hasSize(2);
    assertThat(service.rolesPour(ECOLE_A, cible)).containsExactly(RoleEcole.PARENT);
    assertThat(journalDe(ECOLE_A, cible)).hasSize(3);
  }

  @Test
  void uneOperationSansEffetNEstPasJournalisee() {
    execution.executer(ECOLE_A, () -> service.retirer(cible, RoleEcole.PARENT)); // n'a pas le rôle
    execution.executer(ECOLE_A, () -> service.attribuer(cible, RoleEcole.PARENT));
    execution.executer(ECOLE_A, () -> service.attribuer(cible, RoleEcole.PARENT)); // déjà là
    assertThat(journalDe(ECOLE_A, cible)).hasSize(1);
  }

  @Test
  void leJournalNePeutNiEtreModifieNiSupprimeParLApplication() {
    execution.executer(ECOLE_A, () -> service.attribuer(cible, RoleEcole.PARENT));
    assertThatThrownBy(
            () ->
                execution.executer(
                    ECOLE_A,
                    () -> roleApplicatif().update("UPDATE journal_audit SET action = 'FALSIFIE'")))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied");
    assertThatThrownBy(() -> roleApplicatif().update("DELETE FROM journal_audit"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied");
    assertThatThrownBy(() -> roleApplicatif().update("DELETE FROM appartenance"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  @Test
  void consignerExigeUneTransactionEtUnUtilisateur() {
    assertThatThrownBy(() -> journalAudit.consigner("ESSAI", "Essai", cible, Map.of()))
        .isInstanceOf(IllegalTransactionStateException.class);
    SecurityContextHolder.clearContext();
    assertThatThrownBy(
            () ->
                execution.executer(
                    ECOLE_A, () -> journalAudit.consigner("ESSAI", "Essai", cible, Map.of())))
        .isInstanceOf(IllegalStateException.class);
  }
}
