package sn.ecole.inscription.dev;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.ReferentielScolaire.ClasseScolaire;
import sn.ecole.inscription.application.EleveService;
import sn.ecole.inscription.application.InscriptionService;
import sn.ecole.inscription.application.LienParentalService;
import sn.ecole.inscription.application.ResponsableService;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.inscription.domain.ResponsableRepository;
import sn.ecole.inscription.domain.Sexe;
import sn.ecole.inscription.domain.StatutInscription;
import sn.ecole.inscription.domain.TypeLien;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

/**
 * Profil dev uniquement : quelques élèves MANIFESTEMENT fictifs (« Élève Démo »), un responsable
 * fictif lié à deux d'entre eux (fratrie) et leurs inscriptions dans les classes de l'année en
 * cours. Idempotent ; passe après l'amorçage de l'établissement (années et classes).
 */
@Component
@Profile("dev")
@Order(Ordered.LOWEST_PRECEDENCE)
class DonneesDevInscription implements ApplicationRunner {

  /** Directeur fictif de l'école A (voir realm-ecole.json) : agit pour les contrôles de droits. */
  private static final String DIRECTEUR_DEV = "01930000-0000-7000-8000-000000000101";

  /**
   * Compte Keycloak de dev « parent.demo » (infra/scripts/creer-comptes-dev.sh), relié au
   * responsable de démo.
   */
  private static final UUID PARENT_DEMO = UUID.fromString("01930000-0000-7000-8000-000000000113");

  private static final String TELEPHONE_PARENT_DEMO = "+221700000001";

  private static final List<String> PRENOMS = List.of("Un", "Deux", "Trois", "Quatre");

  private final ExecutionPourTenant execution;
  private final TenantRepository tenants;
  private final ReferentielScolaire referentiel;
  private final EleveService eleves;
  private final ResponsableService responsables;
  private final LienParentalService liens;
  private final InscriptionService inscriptions;
  private final ResponsableRepository responsablesDepot;

  DonneesDevInscription(
      ExecutionPourTenant execution,
      TenantRepository tenants,
      ReferentielScolaire referentiel,
      EleveService eleves,
      ResponsableService responsables,
      LienParentalService liens,
      InscriptionService inscriptions,
      ResponsableRepository responsablesDepot) {
    this.execution = execution;
    this.tenants = tenants;
    this.referentiel = referentiel;
    this.eleves = eleves;
    this.responsables = responsables;
    this.liens = liens;
    this.inscriptions = inscriptions;
    this.responsablesDepot = responsablesDepot;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (Tenant tenant : tenants.findByActifTrueOrderByCode()) {
      agirCommeDirecteur(() -> execution.executer(tenant.getId(), this::semer));
      execution.executer(tenant.getId(), () -> relierParentDemo(tenant));
    }
  }

  /**
   * Idempotent, y compris sur une base déjà amorcée : le parent de démo a un compte de connexion.
   */
  private void relierParentDemo(Tenant tenant) {
    if (!"LYCEE-A".equals(tenant.getCode())) {
      return;
    }
    responsablesDepot.findByTelephoneOrderByNomRecherche(TELEPHONE_PARENT_DEMO).stream()
        .findFirst()
        .filter(r -> r.getUtilisateurId() == null)
        .ifPresent(
            r -> {
              r.relierCompte(PARENT_DEMO);
              responsablesDepot.save(r);
            });
  }

  private void semer() {
    if (!eleves.rechercher(null, null, null, null, null, 1).elements().isEmpty()) {
      return;
    }
    List<ClasseScolaire> classes =
        referentiel.anneeEnCours().map(annee -> referentiel.classes(annee.id())).orElse(List.of());
    UUID parent =
        responsables
            .creer(
                new ResponsableService.Saisie(
                    "Parent", "Démo", "+221700000001", "parent.demo@exemple.test", "Fictive"))
            .getId();
    for (int i = 0; i < PRENOMS.size(); i++) {
      UUID eleve =
          eleves
              .creer(
                  new EleveService.Saisie(
                      "Fictif " + PRENOMS.get(i),
                      "Élève Démo",
                      i % 2 == 0 ? Sexe.F : Sexe.M,
                      LocalDate.of(2012 + i, 1 + i, 10),
                      "Ville Fictive",
                      "SN",
                      null))
              .getId();
      if (i < 2) {
        liens.ajouter(
            eleve, parent, TypeLien.TUTEUR, new LienParental.Droits(true, true, true, true, true));
      }
      if (!classes.isEmpty()) {
        ClasseScolaire classe = classes.get(i % classes.size());
        inscriptions.inscrire(eleve, classe.id(), StatutInscription.INSCRIT, false, List.of());
      }
    }
  }

  private static void agirCommeDirecteur(Runnable action) {
    SecurityContext precedent = SecurityContextHolder.getContext();
    SecurityContext dev = SecurityContextHolder.createEmptyContext();
    dev.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            DIRECTEUR_DEV, "n/a", List.of(new SimpleGrantedAuthority("ROLE_DIRECTEUR"))));
    SecurityContextHolder.setContext(dev);
    try {
      action.run();
    } finally {
      SecurityContextHolder.setContext(precedent);
    }
  }
}
