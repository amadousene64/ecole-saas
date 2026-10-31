package sn.ecole.shared.api;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import sn.ecole.shared.application.AppartenanceService;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({TenantProperties.class, JetonProperties.class})
class SecuriteConfig {

  /** Seul appel de l'API sans école : liste des écoles de l'utilisateur, pour en choisir une. */
  static final String CHEMIN_MES_ECOLES = "/api/v1/moi/ecoles";

  /**
   * Jeton obligatoire, mais PAS d'école : le sélecteur d'école doit pouvoir s'afficher avant d'en
   * avoir choisi une. Chemin exact et méthode GET seulement ; tout autre appel (y compris une
   * variante du chemin) retombe dans {@link #chaineApi} et exige une école dont l'utilisateur est
   * membre. Le service ne lit que les écoles du {@code sub} du jeton.
   */
  @Bean
  @Order(0)
  SecurityFilterChain chaineMesEcoles(HttpSecurity http) throws Exception {
    http.securityMatcher(
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, CHEMIN_MES_ECOLES));
    return configurerApi(http).build();
  }

  /**
   * Activation d'un compte (parent, élève) par le code remis par l'école : l'utilisateur n'est pas
   * encore membre de l'école, il n'y a donc aucun rôle. Jeton obligatoire, école désignée et
   * active, chemin exact et POST seulement ; le service vérifie le code et limite le débit.
   */
  public static final String CHEMIN_ACTIVATION = "/api/v1/activations";

  @Bean
  @Order(0)
  SecurityFilterChain chaineActivation(
      HttpSecurity http,
      TenantProperties proprietes,
      TenantRepository tenants,
      Problemes problemes,
      JetonProperties jeton)
      throws Exception {
    Set<String> clients = Set.copyOf(jeton.clientsActivation());
    FiltreTenant filtre =
        FiltreTenant.sansAppartenance(
            new ResolveurTenantRequete(proprietes, tenants),
            problemes,
            ecole -> tenants.findById(ecole).map(Tenant::isActif).orElse(false));
    http.securityMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, CHEMIN_ACTIVATION))
        .addFilterAfter(filtre, BearerTokenAuthenticationFilter.class);
    // Seule l'application de l'espace parent active un code (azp du jeton) : 403 sinon.
    return configurerApi(
            http,
            (authentification, contexte) ->
                new AuthorizationDecision(
                    authentification.get() instanceof JwtAuthenticationToken j
                        && j.getToken().getClaimAsString("azp") instanceof String client
                        && clients.contains(client)))
        .build();
  }

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
        .addFilterAfter(filtreTenant, BearerTokenAuthenticationFilter.class);
    return configurerApi(http).build();
  }

  private static HttpSecurity configurerApi(HttpSecurity http) throws Exception {
    return configurerApi(http, AuthenticatedAuthorizationManager.authenticated());
  }

  private static HttpSecurity configurerApi(
      HttpSecurity http, AuthorizationManager<RequestAuthorizationContext> acces) throws Exception {
    return http.authorizeHttpRequests(r -> r.anyRequest().access(acces))
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(sansRolesDuJeton())))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // API sans état authentifiée par jeton Bearer (pas de cookie) : pas de CSRF possible.
        .csrf(c -> c.disable())
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
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

  /**
   * Décodeur de jetons (Spring Boot s'efface devant ce bean). Créé à la première requête, pour ne
   * pas exiger Keycloak au démarrage.
   */
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String emetteur,
      @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") List<String> audiences,
      JetonProperties jeton) {
    var validateur = ValidateursJeton.pour(emetteur, audiences, jeton.ensemble());
    return new SupplierJwtDecoder(
        () -> {
          NimbusJwtDecoder decodeur = NimbusJwtDecoder.withIssuerLocation(emetteur).build();
          decodeur.setJwtValidator(validateur);
          return decodeur;
        });
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
