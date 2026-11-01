package sn.ecole.shared.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

  public static final String ROLE_ATTRIBUE = "ROLE_ATTRIBUE";
  public static final String ROLE_RETIRE = "ROLE_RETIRE";
  public static final String CIBLE_UTILISATEUR = "Utilisateur";

  private final AppartenanceRepository appartenances;
  private final TenantRepository tenants;
  private final CacheRoles cache;
  private final ExecutionPourTenant execution;
  private final JournalAudit journal;
  private final Counter echecsSecondeInvalidation;

  /** Métrique : nombre d'échecs de la seconde invalidation (après commit) du cache des rôles. */
  public static final String METRIQUE_ECHEC_INVALIDATION = "ecole.droits.invalidation.echecs";

  public AppartenanceService(
      AppartenanceRepository appartenances,
      TenantRepository tenants,
      CacheRoles cache,
      ExecutionPourTenant execution,
      JournalAudit journal,
      MeterRegistry metriques) {
    this.appartenances = appartenances;
    this.tenants = tenants;
    this.cache = cache;
    this.execution = execution;
    this.journal = journal;
    this.echecsSecondeInvalidation =
        Counter.builder(METRIQUE_ECHEC_INVALIDATION)
            .description("Échecs de l'invalidation du cache des rôles après commit")
            .register(metriques);
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

  /** Donne un rôle à un utilisateur dans l'école courante (journalisé ; sans effet s'il l'a). */
  @PreAuthorize("hasRole('DIRECTEUR')")
  @Transactional
  public void attribuer(UUID utilisateurId, RoleEcole role) {
    ContexteTenant.exiger();
    if (appartenances.findByUtilisateurIdAndRole(utilisateurId, role).isPresent()) {
      return;
    }
    appartenances.save(Appartenance.attribuer(utilisateurId, role));
    journal.consigner(ROLE_ATTRIBUE, CIBLE_UTILISATEUR, utilisateurId, Map.of("role", role));
    changerGenerationAuCommit(utilisateurId);
  }

  /**
   * Donne à l'utilisateur CONNECTÉ le rôle PARENT ou ELEVE dans l'école courante, après
   * vérification par l'appelant d'un code d'activation remis par l'école. Aucun autre rôle n'est
   * possible et l'utilisateur ne peut jamais être un autre que soi : même mal appelée, la méthode
   * ne permet pas d'élever des droits de personnel. Seul le cas d'usage d'activation du module
   * inscription l'appelle (règle ArchUnit), dans sa transaction.
   */
  @PreAuthorize("isAuthenticated()")
  @Transactional(propagation = Propagation.MANDATORY)
  public void rattacherParActivation(RoleEcole role) {
    if (role != RoleEcole.PARENT && role != RoleEcole.ELEVE) {
      throw new IllegalArgumentException("Activation : rôle PARENT ou ELEVE seulement.");
    }
    ContexteTenant.exiger();
    UUID utilisateurId =
        UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    if (appartenances.findByUtilisateurIdAndRole(utilisateurId, role).isPresent()) {
      return;
    }
    appartenances.save(Appartenance.attribuer(utilisateurId, role));
    journal.consigner(
        ROLE_ATTRIBUE, CIBLE_UTILISATEUR, utilisateurId, Map.of("role", role, "par", "ACTIVATION"));
    changerGenerationAuCommit(utilisateurId);
  }

  /** Retire un rôle (suppression logique, journalisée ; sans effet s'il ne l'a pas). */
  @PreAuthorize("hasRole('DIRECTEUR')")
  @Transactional
  public void retirer(UUID utilisateurId, RoleEcole role) {
    ContexteTenant.exiger();
    Optional<Appartenance> active = appartenances.findByUtilisateurIdAndRole(utilisateurId, role);
    if (active.isEmpty()) {
      return;
    }
    active.get().revoquer(Instant.now());
    journal.consigner(ROLE_RETIRE, CIBLE_UTILISATEUR, utilisateurId, Map.of("role", role));
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
              // Pas de reprise (choix assumé) : signalé par journal et par métrique.
              echecsSecondeInvalidation.increment();
              LOG.warn(
                  "Seconde invalidation des droits impossible : une lecture concurrente au commit"
                      + " peut rester en cache jusqu'à expiration ({})",
                  e.toString());
            }
          }
        });
  }
}
