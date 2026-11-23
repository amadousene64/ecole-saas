package sn.ecole.personnel.infrastructure;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.personnel.domain.VerrouAffectations;
import sn.ecole.shared.domain.ContexteTenant;

/**
 * Verrou consultatif PostgreSQL de transaction ({@code pg_advisory_xact_lock}), sur une clé propre
 * à l'école et à la place. Ne lit aucune donnée.
 */
@Component
class VerrouAffectationsPostgres implements VerrouAffectations {

  private final EntityManager em;

  VerrouAffectationsPostgres(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void verrouiller(String place) {
    em.createNativeQuery(
            "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:cle, 0))) verrou")
        .setParameter("cle", "affectation:" + ContexteTenant.exiger() + ":" + place)
        .getSingleResult();
  }
}
