package sn.ecole.shared.application;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/** Rôles d'un utilisateur dans une école : table {@code appartenance} + cache Redis versionné. */
@Service
public class AppartenanceService {

  private static final Logger LOG = LoggerFactory.getLogger(AppartenanceService.class);

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
    // La génération est lue AVANT la base : si les droits changent pendant la lecture, ce que
    // l'on écrit porte une génération déjà périmée et ne sera jamais relu.
    OptionalLong generation = cache.generation(utilisateurId);
    if (generation.isPresent()) {
      Optional<Set<RoleEcole>> enCache =
          cache.lire(tenantId, utilisateurId, generation.getAsLong());
      if (enCache.isPresent()) {
        return enCache.get();
      }
    }
    Optional<Set<RoleEcole>> charges = charger(tenantId, utilisateurId);
    // École inconnue ou inactive : rien en cache (sinon on peut remplir Redis avec des UUID
    // aléatoires).
    if (charges.isPresent() && generation.isPresent()) {
      cache.ecrire(tenantId, utilisateurId, generation.getAsLong(), charges.get());
    }
    return charges.orElseGet(() -> EnumSet.noneOf(RoleEcole.class));
  }

  /** Donne un rôle à un utilisateur dans l'école courante. */
  @PreAuthorize("hasRole('DIRECTEUR')")
  @Transactional
  public void attribuer(UUID utilisateurId, RoleEcole role) {
    ContexteTenant.exiger();
    if (appartenances.findByUtilisateurIdAndRole(utilisateurId, role).isEmpty()) {
      appartenances.save(Appartenance.attribuer(utilisateurId, role));
    }
    changerGenerationAuCommit(utilisateurId);
  }

  /** Retire un rôle à un utilisateur dans l'école courante. */
  @PreAuthorize("hasRole('DIRECTEUR')")
  @Transactional
  public void retirer(UUID utilisateurId, RoleEcole role) {
    ContexteTenant.exiger();
    appartenances.findByUtilisateurIdAndRole(utilisateurId, role).ifPresent(appartenances::delete);
    changerGenerationAuCommit(utilisateurId);
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

  /**
   * Nouvelle génération des droits juste avant le commit (si Redis est injoignable, l'exception
   * annule la transaction : pas de changement de droits sans invalidation) puis juste après (une
   * lecture concurrente qui aurait mis en cache l'état d'avant le commit devient périmée).
   */
  private void changerGenerationAuCommit(UUID utilisateurId) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void beforeCommit(boolean lectureSeule) {
            cache.nouvelleGeneration(utilisateurId);
          }

          @Override
          public void afterCommit() {
            try {
              cache.nouvelleGeneration(utilisateurId);
            } catch (RuntimeException e) {
              LOG.error(
                  "Seconde invalidation des droits impossible : une lecture concurrente au commit"
                      + " peut rester en cache jusqu'à expiration",
                  e);
            }
          }
        });
  }
}
