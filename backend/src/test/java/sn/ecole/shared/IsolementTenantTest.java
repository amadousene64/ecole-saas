package sn.ecole.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_A;
import static sn.ecole.Fixtures.ECOLE_B;
import static sn.ecole.Fixtures.SURVEILLANT_B;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ecole.TestIntegration;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.Appartenance;

/** L'école A ne voit ni ne modifie les données de l'école B, à chaque niveau. */
class IsolementTenantTest extends TestIntegration {

  @Autowired ExecutionPourTenant execution;
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  private static List<UUID> utilisateursVusEnSql(JdbcTemplate connexion) {
    return connexion.queryForList("SELECT utilisateur_id FROM appartenance", UUID.class);
  }

  // --- Niveau SQL : la RLS seule, sans Hibernate ---------------------------------------------

  @Test
  void sqlLeRoleApplicatifNeVoitQueLEcolePositionnee() {
    List<UUID> vus =
        new TransactionTemplate(transactions)
            .execute(
                s -> {
                  jdbc.execute("SELECT set_config('app.tenant_id', '" + ECOLE_A + "', true)");
                  return utilisateursVusEnSql(jdbc);
                });
    assertThat(vus).contains(DIRECTEUR_A).doesNotContain(SURVEILLANT_B);
  }

  @Test
  void sqlUneRequeteSansTenantNeRenvoieAucuneLigne() {
    // Connexion directe, hors transaction Spring : app.tenant_id n'existe pas.
    JdbcTemplate brut = roleApplicatif();
    assertThat(brut.queryForObject("SELECT count(*) FROM appartenance", Integer.class)).isZero();
    assertThat(brut.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
    // Variable positionnée à vide (état après une transaction terminée) : toujours rien, sans
    // erreur.
    Integer apresReset =
        new TransactionTemplate(transactions)
            .execute(
                s -> {
                  jdbc.execute("SELECT set_config('app.tenant_id', '', true)");
                  return jdbc.queryForObject("SELECT count(*) FROM appartenance", Integer.class);
                });
    assertThat(apresReset).isZero();
    // Alors que les lignes existent bel et bien.
    assertThat(
            superUtilisateur().queryForObject("SELECT count(*) FROM appartenance", Integer.class))
        .isPositive();
  }

  @Test
  void sqlInsererUneLignePourBDepuisAEstRefuse() {
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        s -> {
                          jdbc.execute(
                              "SELECT set_config('app.tenant_id', '" + ECOLE_A + "', true)");
                          jdbc.update(
                              "INSERT INTO appartenance (id, tenant_id, utilisateur_id, role)"
                                  + " VALUES (gen_random_uuid(), ?, gen_random_uuid(), 'PARENT')",
                              ECOLE_B);
                        }))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void sqlModifierLesLignesDeBDepuisANeTouchePersonne() {
    Integer modifiees =
        new TransactionTemplate(transactions)
            .execute(
                s -> {
                  jdbc.execute("SELECT set_config('app.tenant_id', '" + ECOLE_A + "', true)");
                  return jdbc.update(
                      "UPDATE appartenance SET modifie_le = now() WHERE tenant_id = ?", ECOLE_B);
                });
    assertThat(modifiees).isZero();
  }

  @Test
  void sqlLeRoleApplicatifNePeutPasSupprimerPhysiquementLOutbox() {
    assertThatThrownBy(() -> roleApplicatif().update("DELETE FROM outbox_event"))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  // --- Niveau JPA : filtre Hibernate @TenantId + RLS ----------------------------------------

  @Test
  void jpaUneRequeteJpqlSansFiltreExpliciteNeVoitQueLEcoleCourante() {
    List<UUID> vusDansA =
        execution.lire(
            ECOLE_A,
            () ->
                em
                    .createQuery("select a from Appartenance a", Appartenance.class)
                    .getResultList()
                    .stream()
                    .map(Appartenance::getUtilisateurId)
                    .toList());
    assertThat(vusDansA).contains(DIRECTEUR_A).doesNotContain(SURVEILLANT_B);
  }

  @Test
  void jpaSansTenantAucuneEntiteNEstVisible() {
    List<Appartenance> vues =
        new TransactionTemplate(transactions)
            .execute(
                s ->
                    em.createQuery("select a from Appartenance a", Appartenance.class)
                        .getResultList());
    assertThat(vues).isEmpty();
  }

  @Test
  void jpaUneRequeteNativeSansTenantNeRenvoieRien() {
    // Même une requête native qui « oublie » le filtre est bloquée par la RLS.
    Number nombre =
        new TransactionTemplate(transactions)
            .execute(
                s ->
                    (Number)
                        em.createNativeQuery("select count(*) from appartenance")
                            .getSingleResult());
    assertThat(nombre.intValue()).isZero();
  }

  @Test
  void leReglageDuTenantNeFuitPasDUneTransactionALAutre() {
    execution.lire(ECOLE_A, () -> utilisateursVusEnSql(jdbc));
    // Transaction suivante, probablement sur la même connexion du pool, sans école.
    String valeur =
        new TransactionTemplate(transactions)
            .execute(
                s ->
                    jdbc.queryForObject(
                        "SELECT coalesce(current_setting('app.tenant_id', true), '')",
                        String.class));
    assertThat(valeur).isEmpty();
  }
}
