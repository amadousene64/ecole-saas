package sn.ecole.etablissement.dev;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import sn.ecole.etablissement.InitialisationEcole;
import sn.ecole.etablissement.StatutEtablissement;
import sn.ecole.etablissement.application.AnneeScolaireService;
import sn.ecole.etablissement.application.ClasseService;
import sn.ecole.etablissement.application.ClasseService.Saisie;
import sn.ecole.etablissement.application.NiveauService;
import sn.ecole.etablissement.application.NiveauService.VueNiveau;
import sn.ecole.etablissement.application.SalleService;
import sn.ecole.etablissement.application.SerieService;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.Serie;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

/**
 * Profil dev uniquement : donne à chaque école fictive son profil, le modèle sénégalais, une année
 * 2026-2027 ouverte, trois salles et quelques classes. Idempotent ; jamais de données réelles.
 *
 * <p>Hors des couches api / application / domain / infrastructure : c'est un amorçage qui pilote
 * les cas d'usage, comme le ferait un client de l'API.
 */
@Component
@Profile("dev")
class DonneesDev implements ApplicationRunner {

  /** Directeur fictif de l'école A (voir realm-ecole.json) : agit pour les contrôles de droits. */
  private static final String DIRECTEUR_DEV = "01930000-0000-7000-8000-000000000101";

  private final ExecutionPourTenant execution;
  private final TenantRepository tenants;
  private final InitialisationEcole initialisation;
  private final AnneeScolaireService annees;
  private final SalleService salles;
  private final ClasseService classes;
  private final NiveauService niveaux;
  private final SerieService series;

  DonneesDev(
      ExecutionPourTenant execution,
      TenantRepository tenants,
      InitialisationEcole initialisation,
      AnneeScolaireService annees,
      SalleService salles,
      ClasseService classes,
      NiveauService niveaux,
      SerieService series) {
    this.execution = execution;
    this.tenants = tenants;
    this.initialisation = initialisation;
    this.annees = annees;
    this.salles = salles;
    this.classes = classes;
    this.niveaux = niveaux;
    this.series = series;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (Tenant tenant : tenants.findByActifTrueOrderByCode()) {
      UUID ecole = tenant.getId();
      if (!initialisation.estInitialisee(ecole)) {
        initialisation.initialiser(
            ecole, tenant.getNom(), tenant.getCode(), StatutEtablissement.PRIVE_LAIQUE);
      }
      boolean lycee = tenant.getCode().startsWith("LYCEE");
      agirCommeDirecteur(() -> execution.executer(ecole, () -> semer(lycee)));
    }
  }

  private void semer(boolean lycee) {
    if (!annees.lister().isEmpty()) {
      return;
    }
    AnneeScolaire annee =
        annees.creer("2026-2027", LocalDate.of(2026, 10, 5), LocalDate.of(2027, 7, 2));
    annees.ouvrir(annee.getId());
    for (int numero = 1; numero <= 3; numero++) {
      salles.creer("Salle " + numero, 50);
    }
    if (lycee) {
      classe(annee, "TLE", "S2", "TS2B", 45);
      classe(annee, "4E", null, "4C", 45);
    }
    classe(annee, "CE1", null, "CE1A", 40);
    classe(annee, "CM2", null, "CM2B", 40);
  }

  private void classe(AnneeScolaire annee, String niveau, String serie, String nom, int capacite) {
    UUID niveauId =
        niveaux.lister().stream()
            .map(VueNiveau::niveau)
            .filter(n -> n.getCode().equals(niveau))
            .findFirst()
            .orElseThrow()
            .getId();
    UUID serieId =
        serie == null
            ? null
            : series.lister().stream()
                .filter(s -> s.getCode().equals(serie))
                .map(Serie::getId)
                .findFirst()
                .orElseThrow();
    classes.creer(annee.getId(), new Saisie(niveauId, serieId, nom, capacite, null));
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
