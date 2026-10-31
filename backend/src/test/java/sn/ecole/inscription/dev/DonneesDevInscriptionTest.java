package sn.ecole.inscription.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.inscription.TestInscription;
import sn.ecole.inscription.application.EleveService;
import sn.ecole.inscription.application.InscriptionService;
import sn.ecole.inscription.application.LienParentalService;
import sn.ecole.inscription.application.ResponsableService;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.TenantRepository;
import tools.jackson.databind.JsonNode;

/** Amorçage du profil dev : élèves fictifs, une fratrie, inscriptions ; idempotent. */
class DonneesDevInscriptionTest extends TestInscription {

  @Autowired ExecutionPourTenant execution;
  @Autowired TenantRepository tenants;
  @Autowired ReferentielScolaire referentiel;
  @Autowired EleveService eleves;
  @Autowired ResponsableService responsables;
  @Autowired LienParentalService liens;
  @Autowired InscriptionService inscriptions;

  private DonneesDevInscription donnees() {
    return new DonneesDevInscription(
        execution, tenants, referentiel, eleves, responsables, liens, inscriptions);
  }

  private List<String> noms() throws Exception {
    List<String> noms = new ArrayList<>();
    for (JsonNode e : get(DIRECTEUR_A, A, ELEVES).corps().path("elements")) {
      noms.add(e.path("nom").asText() + " / " + e.path("classe").asText(""));
    }
    return noms;
  }

  @Test
  void desElevesManifestementFictifsSontInscritsUneSeuleFois() throws Exception {
    var annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    post(
        DIRECTEUR_A,
        A,
        "/api/v1/classes",
        corps(
            "anneeId",
            annee,
            "niveauId",
            niveau(DIRECTEUR_A, A, "CM2"),
            "nom",
            "CM2B",
            "capacite",
            40));

    donnees().run(null);
    List<String> apres = noms();
    assertThat(apres).hasSize(4).allMatch(n -> n.startsWith("ÉLÈVE DÉMO / CM2B"));

    donnees().run(null);
    assertThat(noms()).isEqualTo(apres);
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT count(*) FROM ecole.lien_parental WHERE tenant_id = ?",
                    Integer.class,
                    A))
        .isEqualTo(2);
  }
}
