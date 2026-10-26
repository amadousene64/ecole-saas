package sn.ecole.shared.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;
import sn.ecole.shared.domain.ContexteTenant;

/**
 * Gestionnaire de transactions JPA qui, au début de chaque transaction physique, exécute {@code
 * set_config('app.tenant_id', <école courante>, true)}. Le réglage est local à la transaction : il
 * disparaît au commit ou au rollback et ne peut pas passer à une autre requête via le pool. Sans
 * école courante, la valeur est vide et les politiques RLS ne laissent voir aucune ligne.
 */
public class TenantJpaTransactionManager extends JpaTransactionManager {

  public TenantJpaTransactionManager(EntityManagerFactory emf) {
    super(emf);
    setJpaDialect(new DialecteTenant());
  }

  /**
   * Le conteneur Spring rappelle cette méthode, qui reprend le dialecte de l'EntityManagerFactory :
   * on remet le nôtre ensuite, sinon {@code app.tenant_id} ne serait jamais positionné.
   */
  @Override
  public void afterPropertiesSet() {
    super.afterPropertiesSet();
    setJpaDialect(new DialecteTenant());
  }

  static final class DialecteTenant extends HibernateJpaDialect {

    private static final String SQL = "select set_config('app.tenant_id', ?, true)";

    @Override
    public Object beginTransaction(EntityManager em, TransactionDefinition definition)
        throws SQLException {
      Object donnees = super.beginTransaction(em, definition);
      String tenant = ContexteTenant.courant().map(UUID::toString).orElse("");
      em.unwrap(Session.class)
          .doWork(
              connexion -> {
                try (PreparedStatement ps = connexion.prepareStatement(SQL)) {
                  ps.setString(1, tenant);
                  ps.execute();
                }
              });
      return donnees;
    }
  }
}
