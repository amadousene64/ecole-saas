package sn.ecole.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.ECOLE_A;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.ecole.Fixtures;
import sn.ecole.TestIntegration;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.Appartenance;
import sn.ecole.shared.domain.AppartenanceRepository;
import sn.ecole.shared.domain.OutboxEvent;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Les lignes dont {@code supprime_le} est renseigné sont invisibles pour toute entité. */
class SuppressionLogiqueTest extends TestIntegration {

  @Autowired ExecutionPourTenant execution;
  @Autowired EntityManager em;
  @Autowired AppartenanceRepository appartenances;

  private UUID appartenanceSupprimee(UUID utilisateur) {
    Fixtures.appartenance(superUtilisateur(), ECOLE_A, utilisateur, "PARENT");
    superUtilisateur()
        .update(
            "UPDATE appartenance SET supprime_le = now() WHERE utilisateur_id = ?", utilisateur);
    return superUtilisateur()
        .queryForObject(
            "SELECT id FROM appartenance WHERE utilisateur_id = ?", UUID.class, utilisateur);
  }

  @Test
  void uneAppartenanceSupprimeeEstInvisiblePartout() {
    UUID utilisateur = UUID.randomUUID();
    UUID id = appartenanceSupprimee(utilisateur);

    execution.lire(
        ECOLE_A,
        () -> {
          assertThat(appartenances.findByUtilisateurId(utilisateur)).isEmpty();
          assertThat(appartenances.findByUtilisateurIdAndRole(utilisateur, RoleEcole.PARENT))
              .isEmpty();
          assertThat(
                  em.createQuery(
                          "select a from Appartenance a where a.utilisateurId = :u",
                          Appartenance.class)
                      .setParameter("u", utilisateur)
                      .getResultList())
              .isEmpty();
          assertThat(em.find(Appartenance.class, id)).isNull();
          return null;
        });
  }

  @Test
  void unEvenementOutboxSupprimeEstInvisible() {
    UUID agregat = UUID.randomUUID();
    superUtilisateur()
        .update(
            """
            INSERT INTO outbox_event (id, tenant_id, type_agregat, id_agregat, type_evenement,
                                      charge, supprime_le)
            VALUES (gen_random_uuid(), ?, 'Essai', ?, 'Essai', '{}', now())""",
            ECOLE_A,
            agregat);

    List<OutboxEvent> visibles =
        execution.lire(
            ECOLE_A,
            () ->
                em.createQuery(
                        "select e from OutboxEvent e where e.idAgregat = :a", OutboxEvent.class)
                    .setParameter("a", agregat)
                    .getResultList());
    assertThat(visibles).isEmpty();
  }

  @Test
  void laRestrictionSAppliqueAToutesLesEntitesMetier() {
    // Garde-fou pour les modules à venir : chaque entité métier hérite de la restriction.
    appartenanceSupprimee(UUID.randomUUID());
    unEvenementOutboxSupprimeEstInvisible();
    List<Class<?>> entitesMetier =
        em.getMetamodel().getEntities().stream()
            .map(EntityType::getJavaType)
            .<Class<?>>map(c -> c)
            .filter(TenantAwareEntity.class::isAssignableFrom)
            .toList();
    assertThat(entitesMetier).contains(Appartenance.class, OutboxEvent.class);
    for (Class<?> entite : entitesMetier) {
      Long total =
          execution.lire(
              ECOLE_A,
              () ->
                  em.createQuery(
                          "select count(e) from "
                              + entite.getSimpleName()
                              + " e where e.supprimeLe is not null",
                          Long.class)
                      .getSingleResult());
      assertThat(total).as("lignes supprimées visibles pour %s", entite).isZero();
    }
  }
}
