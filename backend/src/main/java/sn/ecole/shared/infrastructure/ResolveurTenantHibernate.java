package sn.ecole.shared.infrastructure;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import sn.ecole.shared.domain.ContexteTenant;

/**
 * Fournit à Hibernate l'école courante pour {@code @TenantId}. Sans école, renvoie {@link
 * ContexteTenant#AUCUN}, qui ne correspond à aucune ligne (et qu'aucune insertion ne peut utiliser
 * : clé étrangère vers {@code tenant} + RLS).
 */
public class ResolveurTenantHibernate implements CurrentTenantIdentifierResolver<UUID> {

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    return ContexteTenant.courant().orElse(ContexteTenant.AUCUN);
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }
}
