package sn.ecole.etablissement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.ecole.etablissement.TestEtablissement;
import sn.ecole.etablissement.application.AnneeScolaireService;
import sn.ecole.etablissement.application.ClasseService;
import sn.ecole.etablissement.application.NiveauService;
import sn.ecole.etablissement.application.SalleService;
import sn.ecole.etablissement.application.SerieService;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.TenantRepository;

/** Le jeu de données du profil dev (non actif en test) est complet et rejouable. */
class DonneesDevTest extends TestEtablissement {

  @Autowired ExecutionPourTenant execution;
  @Autowired TenantRepository tenants;
  @Autowired AnneeScolaireService annees;
  @Autowired SalleService salles;
  @Autowired ClasseService classes;
  @Autowired NiveauService niveaux;
  @Autowired SerieService series;

  private DonneesDev donnees() {
    return new DonneesDev(
        execution, tenants, initialisation, annees, salles, classes, niveaux, series);
  }

  private List<String> noms(Reponse r) {
    List<String> noms = new ArrayList<>();
    r.corps().forEach(c -> noms.add(c.path("nom").asText()));
    return noms;
  }

  @Test
  void lesEcolesFictivesRecoiventAnneeSallesEtClasses() throws Exception {
    donnees().run(null);

    Reponse anneeA = get(DIRECTEUR_A, A, "/api/v1/annees-scolaires/en-cours");
    assertThat(anneeA.statut()).isEqualTo(200);
    assertThat(anneeA.corps().path("libelle").asText()).isEqualTo("2026-2027");
    assertThat(anneeA.corps().path("statut").asText()).isEqualTo("EN_COURS");
    assertThat(get(DIRECTEUR_A, A, "/api/v1/salles").corps()).hasSize(3);
    assertThat(noms(get(DIRECTEUR_A, A, "/api/v1/classes")))
        .containsExactly("4C", "CE1A", "CM2B", "TS2B");
    assertThat(noms(get(DIRECTEUR_B, B, "/api/v1/classes"))).containsExactly("CE1A", "CM2B");
    assertThat(get(DIRECTEUR_B, B, "/api/v1/annees-scolaires/en-cours").id())
        .isNotEqualTo(anneeA.id());
  }

  @Test
  void lesRelancesNeDupliquentRien() throws Exception {
    donnees().run(null);
    donnees().run(null);

    assertThat(get(DIRECTEUR_A, A, "/api/v1/annees-scolaires").corps()).hasSize(1);
    assertThat(get(DIRECTEUR_A, A, "/api/v1/salles").corps()).hasSize(3);
    assertThat(get(DIRECTEUR_A, A, "/api/v1/classes").corps()).hasSize(4);
  }
}
