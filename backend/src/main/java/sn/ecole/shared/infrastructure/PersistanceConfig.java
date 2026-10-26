package sn.ecole.shared.infrastructure;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditeurCourant")
class PersistanceConfig {

  /** Remplace le gestionnaire de Spring Boot (qui s'efface devant ce bean). */
  @Bean
  JpaTransactionManager transactionManager(
      EntityManagerFactory emf, ObjectProvider<TransactionManagerCustomizers> personnalisations) {
    TenantJpaTransactionManager gestionnaire = new TenantJpaTransactionManager(emf);
    personnalisations.ifAvailable(p -> p.customize(gestionnaire));
    return gestionnaire;
  }

  @Bean
  HibernatePropertiesCustomizer resolveurTenantHibernate() {
    return proprietes ->
        proprietes.put(
            MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new ResolveurTenantHibernate());
  }
}
