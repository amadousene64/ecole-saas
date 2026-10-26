package sn.ecole.shared.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Renseigne {@code cree_par} / {@code modifie_par} avec le {@code sub} Keycloak. */
@Component("auditeurCourant")
class AuditeurCourant implements AuditorAware<UUID> {

  @Override
  public Optional<UUID> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jeton)) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(jeton.getName()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
