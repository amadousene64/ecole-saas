package sn.ecole.inscription.infrastructure;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.VerrouClasse;
import sn.ecole.shared.domain.ContexteTenant;

/**
 * Verrou consultatif PostgreSQL de transaction ({@code pg_advisory_xact_lock}), sur une clé propre
 * à l'école et à la classe. Ne lit aucune donnée.
 */
@Component
class VerrouClassePostgres implements VerrouClasse {

  private final EntityManager em;

  VerrouClassePostgres(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void verrouiller(UUID classeId) {
    em.createNativeQuery(
            "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:cle, 0))) verrou")
        .setParameter("cle", "classe:" + ContexteTenant.exiger() + ":" + classeId)
        .getSingleResult();
  }
}
