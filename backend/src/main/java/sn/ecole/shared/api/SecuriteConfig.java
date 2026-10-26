package sn.ecole.shared.api;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import sn.ecole.shared.application.AppartenanceService;
import sn.ecole.shared.domain.TenantRepository;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(TenantProperties.class)
class SecuriteConfig {

  /** API : jeton obligatoire, puis vérification de l'appartenance à l'école demandée. */
  @Bean
  @Order(1)
  SecurityFilterChain chaineApi(
      HttpSecurity http,
      TenantProperties proprietes,
      TenantRepository tenants,
      AppartenanceService appartenances,
      Problemes problemes)
      throws Exception {
    FiltreTenant filtreTenant =
        new FiltreTenant(new ResolveurTenantRequete(proprietes, tenants), appartenances, problemes);
    http.securityMatcher("/api/**")
        .authorizeHttpRequests(r -> r.anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(sansRolesDuJeton())))
        .addFilterAfter(filtreTenant, BearerTokenAuthenticationFilter.class)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // API sans état authentifiée par jeton Bearer (pas de cookie) : pas de CSRF possible.
        .csrf(c -> c.disable())
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
    return http.build();
  }

  /** Le reste : santé publique, documentation (activée seulement en dev), tout le reste refusé. */
  @Bean
  @Order(2)
  SecurityFilterChain chaineAutres(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            r ->
                r.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(c -> c.disable());
    return http.build();
  }

  /** Les rôles ne viennent jamais du jeton (ni scopes ni rôles de realm) : voir FiltreTenant. */
  static JwtAuthenticationConverter sansRolesDuJeton() {
    JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
    convertisseur.setJwtGrantedAuthoritiesConverter(jwt -> List.of());
    return convertisseur;
  }

  @Bean
  static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      @Lazy PermissionEvaluator evaluateur) {
    DefaultMethodSecurityExpressionHandler gestionnaire =
        new DefaultMethodSecurityExpressionHandler();
    gestionnaire.setPermissionEvaluator(evaluateur);
    return gestionnaire;
  }
}
