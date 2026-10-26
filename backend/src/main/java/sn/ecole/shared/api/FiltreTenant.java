package sn.ecole.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import sn.ecole.shared.application.AppartenanceService;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Placé juste après l'authentification par jeton. Pour chaque appel {@code /api/**} authentifié :
 *
 * <ol>
 *   <li>détermine l'école demandée (en-tête en dev, sous-domaine en prod) ;
 *   <li>vérifie dans {@code appartenance} que l'utilisateur en est membre ;
 *   <li>remplace les autorités par ses rôles dans CETTE école uniquement ;
 *   <li>ouvre le contexte d'école pour la suite du traitement (RLS + filtre Hibernate).
 * </ol>
 *
 * Aucun rôle n'est lu dans le jeton Keycloak.
 */
class FiltreTenant extends OncePerRequestFilter {

  private final ResolveurTenantRequete resolveur;
  private final AppartenanceService appartenances;
  private final Problemes problemes;
  private final SecurityContextHolderStrategy contextes =
      SecurityContextHolder.getContextHolderStrategy();

  FiltreTenant(
      ResolveurTenantRequete resolveur, AppartenanceService appartenances, Problemes problemes) {
    this.resolveur = resolveur;
    this.appartenances = appartenances;
    this.problemes = problemes;
  }

  // Pas de shouldNotFilter : le filtre n'est enregistré que dans la chaîne /api/**, et un test
  // maison sur l'URI brute (non décodée) permettait de le contourner avec /%61pi/...

  @Override
  protected void doFilterInternal(
      HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
      throws ServletException, IOException {
    if (!(contextes.getContext().getAuthentication() instanceof JwtAuthenticationToken jeton)) {
      chaine.doFilter(requete, reponse); // l'autorisation répondra 401
      return;
    }
    UUID tenantId;
    try {
      tenantId = verifierAppartenance(requete, jeton);
    } catch (ErreurMetier e) {
      contextes.clearContext();
      problemes.ecrire(requete, reponse, e);
      return;
    }
    try {
      ContexteTenant.<Void, Exception>executer(
          tenantId,
          () -> {
            chaine.doFilter(requete, reponse);
            return null;
          });
    } catch (IOException | ServletException | RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  private UUID verifierAppartenance(HttpServletRequest requete, JwtAuthenticationToken jeton) {
    Optional<UUID> demande = resolveur.resoudre(requete);
    UUID tenantId =
        demande.orElseThrow(
            () ->
                new ErreurMetier(
                    "TENANT_ABSENT", TypeErreur.INVALIDE, "Aucune école n'est désignée."));
    UUID utilisateurId = identifiant(jeton);
    Set<RoleEcole> roles = appartenances.rolesPour(tenantId, utilisateurId);
    if (roles.isEmpty()) {
      throw new ErreurMetier(
          "TENANT_NON_AUTORISE", TypeErreur.INTERDIT, "Vous n'avez pas accès à cette école.");
    }
    List<GrantedAuthority> autorites =
        roles.stream()
            .map(RoleEcole::autorite)
            .<GrantedAuthority>map(SimpleGrantedAuthority::new)
            .toList();
    JwtAuthenticationToken avecRoles =
        new JwtAuthenticationToken(jeton.getToken(), autorites, jeton.getName());
    avecRoles.setDetails(jeton.getDetails());
    SecurityContext contexte = contextes.createEmptyContext();
    contexte.setAuthentication(avecRoles);
    contextes.setContext(contexte);
    return tenantId;
  }

  private static UUID identifiant(JwtAuthenticationToken jeton) {
    try {
      return UUID.fromString(jeton.getName());
    } catch (IllegalArgumentException e) {
      throw new ErreurMetier(
          "JETON_INVALIDE", TypeErreur.NON_AUTHENTIFIE, "Identifiant d'utilisateur invalide.");
    }
  }
}
