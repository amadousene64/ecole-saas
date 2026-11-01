package sn.ecole.inscription.infrastructure;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.CompteurMatricule;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.Identifiants;

/**
 * Incrément atomique par {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING} : deux créations
 * simultanées obtiennent deux numéros distincts. Filtre tenant explicite (et RLS).
 */
@Component
class CompteurMatriculeSql implements CompteurMatricule {

  private final EntityManager em;

  CompteurMatriculeSql(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public long suivant(String cle) {
    Object valeur =
        em.createNativeQuery(
                """
                INSERT INTO sequence_matricule (id, tenant_id, cle, valeur)
                VALUES (:id, :tenant, :cle, 1)
                ON CONFLICT (tenant_id, cle)
                DO UPDATE SET valeur = sequence_matricule.valeur + 1, modifie_le = now()
                WHERE sequence_matricule.tenant_id = :tenant
                RETURNING valeur""")
            .setParameter("id", Identifiants.uuidV7())
            .setParameter("tenant", ContexteTenant.exiger())
            .setParameter("cle", cle)
            .getSingleResult();
    return ((Number) valeur).longValue();
  }
}
