package sn.ecole.personnel.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.ReferentielScolaire.AnneeEtCycle;
import sn.ecole.personnel.domain.Affectation;
import sn.ecole.personnel.domain.AffectationRepository;
import sn.ecole.personnel.domain.CachePerimetre;
import sn.ecole.personnel.domain.CachePerimetre.Calcule;
import sn.ecole.personnel.domain.CachePerimetre.CycleSurveille;
import sn.ecole.personnel.domain.TypeAffectation;
import sn.ecole.shared.application.PerimetreScolaire;
import sn.ecole.shared.domain.CacheRoles;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Périmètre de l'utilisateur connecté, d'après ses rôles et ses affectations en vigueur aujourd'hui
 * (horloge de l'application, fuseau de Dakar). Refus par défaut : sans rôle du personnel ou sans
 * affectation, aucune classe.
 *
 * <p>Mis en cache par utilisateur, par jour et par génération des droits ({@link CacheRoles}) :
 * créer, modifier ou terminer une affectation change la génération au commit, l'effet est immédiat.
 * Sans cache (panne Redis), les affectations sont relues à chaque appel.
 */
@Component
class PerimetreScolaireService implements PerimetreScolaire {

  /** Rôles qui voient toute l'école. */
  private static final Set<RoleEcole> TOUTE_L_ECOLE =
      EnumSet.of(
          RoleEcole.DIRECTEUR, RoleEcole.CENSEUR, RoleEcole.SECRETARIAT, RoleEcole.COMPTABLE);

  private final AffectationRepository affectations;
  private final ReferentielScolaire referentiel;
  private final CacheRoles generations;
  private final CachePerimetre cache;
  private final Clock horloge;

  PerimetreScolaireService(
      AffectationRepository affectations,
      ReferentielScolaire referentiel,
      CacheRoles generations,
      CachePerimetre cache,
      Clock horloge) {
    this.affectations = affectations;
    this.referentiel = referentiel;
    this.generations = generations;
    this.cache = cache;
    this.horloge = horloge;
  }

  @Override
  public boolean toutLEcole() {
    return roles().stream().anyMatch(TOUTE_L_ECOLE::contains);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean voitClasse(UUID classeId) {
    return classeId != null && !filtrer(List.of(classeId)).isEmpty();
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> filtrer(Collection<UUID> classeIds) {
    if (toutLEcole()) {
      return new LinkedHashSet<>(classeIds);
    }
    Calcule perimetre = calcule();
    Set<UUID> visibles = new LinkedHashSet<>();
    List<UUID> aVerifier = new ArrayList<>();
    for (UUID id : classeIds) {
      if (perimetre.classes().contains(id)) {
        visibles.add(id);
      } else if (!perimetre.cycles().isEmpty()) {
        aVerifier.add(id);
      }
    }
    if (!aVerifier.isEmpty()) {
      Map<UUID, AnneeEtCycle> classes = referentiel.anneesEtCyclesDesClasses(aVerifier);
      for (UUID id : classeIds) {
        AnneeEtCycle ac = classes.get(id);
        if (ac != null
            && perimetre.cycles().contains(new CycleSurveille(ac.anneeId(), ac.codeCycle()))) {
          visibles.add(id);
        }
      }
    }
    return visibles;
  }

  /** Classes et cycles en vigueur aujourd'hui pour l'utilisateur connecté, selon ses rôles. */
  private Calcule calcule() {
    Set<RoleEcole> roles = roles();
    boolean enseignant = roles.contains(RoleEcole.ENSEIGNANT);
    boolean surveillant = roles.contains(RoleEcole.SURVEILLANT);
    if (!enseignant && !surveillant) {
      return Calcule.AUCUN;
    }
    UUID moi = utilisateur();
    UUID ecole = ContexteTenant.exiger();
    LocalDate aujourdhui = LocalDate.now(horloge);
    OptionalLong generation = generations.generation(moi);
    if (generation.isPresent()) {
      var enCache = cache.lire(ecole, moi, generation.getAsLong(), aujourdhui);
      if (enCache.isPresent()) {
        return filtrerParRole(enCache.get(), enseignant, surveillant);
      }
    }
    Set<UUID> classes = new HashSet<>();
    Set<CycleSurveille> cyclesSurveilles = new HashSet<>();
    for (Affectation a : affectations.findByUtilisateurId(moi)) {
      if (!a.estEnCoursLe(aujourdhui)) {
        continue;
      }
      if (a.getType() == TypeAffectation.SURVEILLANCE) {
        cyclesSurveilles.add(new CycleSurveille(a.getAnneeId(), a.getCodeCycle()));
      } else {
        classes.add(a.getClasseId());
      }
    }
    Calcule lu = new Calcule(classes, cyclesSurveilles);
    if (generation.isPresent()) {
      cache.ecrire(ecole, moi, generation.getAsLong(), aujourdhui, lu);
    }
    return filtrerParRole(lu, enseignant, surveillant);
  }

  /** Une affectation ne vaut qu'avec le rôle correspondant : un rôle retiré retire le périmètre. */
  private static Calcule filtrerParRole(Calcule c, boolean enseignant, boolean surveillant) {
    return new Calcule(enseignant ? c.classes() : Set.of(), surveillant ? c.cycles() : Set.of());
  }

  private static Set<RoleEcole> roles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Set<RoleEcole> roles = EnumSet.noneOf(RoleEcole.class);
    if (auth == null) {
      return roles;
    }
    for (var autorite : auth.getAuthorities()) {
      for (RoleEcole role : RoleEcole.values()) {
        if (role.autorite().equals(autorite.getAuthority())) {
          roles.add(role);
        }
      }
    }
    return roles;
  }

  private static UUID utilisateur() {
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }
}
