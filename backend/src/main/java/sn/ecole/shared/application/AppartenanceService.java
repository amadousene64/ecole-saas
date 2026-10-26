package sn.ecole.shared.application;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sn.ecole.shared.domain.Appartenance;
import sn.ecole.shared.domain.AppartenanceRepository;
import sn.ecole.shared.domain.CacheRoles;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

/** Rôles d'un utilisateur dans une école : table {@code appartenance} + cache Redis. */
@Service
public class AppartenanceService {

  private final AppartenanceRepository appartenances;
  private final TenantRepository tenants;
  private final CacheRoles cache;
  private final ExecutionPourTenant execution;

  public AppartenanceService(
      AppartenanceRepository appartenances,
      TenantRepository tenants,
      CacheRoles cache,
      ExecutionPourTenant execution) {
    this.appartenances = appartenances;
    this.tenants = tenants;
    this.cache = cache;
    this.execution = execution;
  }

  /**
   * Rôles de l'utilisateur dans CETTE école uniquement ; vide s'il n'en est pas membre ou si
   * l'école est inconnue ou inactive. Appelé par le filtre de tenant, avant l'ouverture du contexte
   * d'école.
   */
  @PreAuthorize("isAuthenticated()")
  public Set<RoleEcole> rolesPour(UUID tenantId, UUID utilisateurId) {
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(utilisateurId);
    return cache
        .lire(tenantId, utilisateurId)
        .orElseGet(
            () ->
                charger(tenantId, utilisateurId)
                    .map(
                        roles -> {
                          cache.ecrire(tenantId, utilisateurId, roles);
                          return roles;
                        })
                    // École inconnue ou inactive : rien en cache (sinon on peut remplir Redis
                    // avec des UUID aléatoires).
                    .orElseGet(() -> EnumSet.noneOf(RoleEcole.class)));
  }

  /** Donne un rôle à un utilisateur dans l'école courante. */
  @PreAuthorize("hasRole('DIRECTEUR')")
  @Transactional
  public void attribuer(UUID utilisateurId, RoleEcole role) {
    UUID tenantId = ContexteTenant.exiger();
    if (appartenances.findByUtilisateurIdAndRole(utilisateurId, role).isEmpty()) {
      appartenances.save(Appartenance.attribuer(utilisateurId, role));
    }
    invaliderApresValidation(tenantId, utilisateurId);
  }

  /** Retire un rôle à un utilisateur dans l'école courante. */
  @PreAuthorize("hasRole('DIRECTEUR')")
  @Transactional
  public void retirer(UUID utilisateurId, RoleEcole role) {
    UUID tenantId = ContexteTenant.exiger();
    appartenances.findByUtilisateurIdAndRole(utilisateurId, role).ifPresent(appartenances::delete);
    invaliderApresValidation(tenantId, utilisateurId);
  }

  /** Vide si l'école est inconnue ou inactive. */
  private Optional<Set<RoleEcole>> charger(UUID tenantId, UUID utilisateurId) {
    return execution.lire(
        tenantId,
        () -> {
          if (!tenants.findById(tenantId).map(Tenant::isActif).orElse(false)) {
            return Optional.empty();
          }
          Set<RoleEcole> roles = EnumSet.noneOf(RoleEcole.class);
          appartenances.findByUtilisateurId(utilisateurId).forEach(a -> roles.add(a.getRole()));
          return Optional.of(roles);
        });
  }

  private void invaliderApresValidation(UUID tenantId, UUID utilisateurId) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            cache.invalider(tenantId, utilisateurId);
          }
        });
  }
}
