package sn.ecole.shared.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** Validation complète d'un jeton d'accès : émetteur, dates, audience et client autorisé. */
final class ValidateursJeton {

  private ValidateursJeton() {}

  static OAuth2TokenValidator<Jwt> pour(
      String emetteur, Collection<String> audiences, Set<String> clientsAutorises) {
    return new DelegatingOAuth2TokenValidator<>(
        List.of(
            JwtValidators.createDefaultWithIssuer(emetteur),
            audience(Set.copyOf(audiences)),
            clientAutorise(clientsAutorises)));
  }

  static OAuth2TokenValidator<Jwt> audience(Set<String> audiences) {
    return jwt ->
        jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
            ? OAuth2TokenValidatorResult.success()
            : echec("Audience du jeton non acceptée.");
  }

  /** {@code azp} : client Keycloak qui a obtenu le jeton. Absent ou inconnu = refusé. */
  static OAuth2TokenValidator<Jwt> clientAutorise(Set<String> clientsAutorises) {
    return jwt -> {
      String client = jwt.getClaimAsString("azp");
      return client != null && clientsAutorises.contains(client)
          ? OAuth2TokenValidatorResult.success()
          : echec("Client non autorisé pour cette API.");
    };
  }

  private static OAuth2TokenValidatorResult echec(String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
  }
}
