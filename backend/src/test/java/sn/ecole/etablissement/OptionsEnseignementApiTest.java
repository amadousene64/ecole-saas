package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.PARENT_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import sn.ecole.shared.application.ExecutionPourTenant;
import tools.jackson.databind.JsonNode;

/** Catalogue des options d'enseignement et options proposées par chaque classe. */
class OptionsEnseignementApiTest extends TestEtablissement {

  private static final String OPTIONS = "/api/v1/options-enseignement";

  @Autowired ReferentielScolaire referentiel;
  @Autowired ExecutionPourTenant execution;

  private UUID annee;
  private UUID classe;

  @BeforeEach
  void classeDeSeconde() throws Exception {
    annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    classe =
        post(
                DIRECTEUR_A,
                A,
                "/api/v1/classes",
                corps(
                    "anneeId",
                    annee,
                    "niveauId",
                    niveau(DIRECTEUR_A, A, "2NDE"),
                    "serieId",
                    serie(DIRECTEUR_A, A, "S"),
                    "nom",
                    "2NDE S1",
                    "capacite",
                    40))
            .id();
  }

  private UUID creerOption(UUID ecole, UUID qui, String code, String groupe) throws Exception {
    Reponse r =
        post(
            qui,
            ecole,
            OPTIONS,
            corps("code", code, "libelle", "Option " + code, "groupe", groupe));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  private <T> T commeRole(String role, Supplier<T> action) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                DIRECTEUR_A.toString(), "n/a", List.of(new SimpleGrantedAuthority(role))));
    try {
      return execution.lire(A, action);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static List<String> codes(Reponse r) {
    List<String> codes = new ArrayList<>();
    for (JsonNode n : r.corps()) {
      codes.add(n.path("code").asText());
    }
    return codes;
  }

  @Test
  void leSecretariatGereLeCatalogueEtLeCodeEstUnique() throws Exception {
    UUID esp = creerOption(A, SECRETARIAT_A, "esp", "lv2");
    Reponse lue = get(DIRECTEUR_A, A, OPTIONS);
    assertThat(lue.corps().get(0).path("code").asText()).isEqualTo("ESP");
    assertThat(lue.corps().get(0).path("groupe").asText()).isEqualTo("LV2");

    Reponse doublon =
        post(SECRETARIAT_A, A, OPTIONS, corps("code", "ESP", "libelle", "Autre", "groupe", "LV2"));
    assertThat(doublon.statut()).isEqualTo(409);
    assertThat(doublon.code()).isEqualTo("OPTION_EXISTANTE");

    Reponse modifiee =
        put(
            SECRETARIAT_A,
            A,
            OPTIONS + "/" + esp,
            0,
            corps("code", "ESP", "libelle", "Espagnol", "groupe", "LV2", "actif", false));
    assertThat(modifiee.statut()).isEqualTo(200);
    assertThat(modifiee.corps().path("actif").asBoolean()).isFalse();
  }

  @Test
  void uneClasseProposeDesOptionsDuCatalogue() throws Exception {
    UUID esp = creerOption(A, DIRECTEUR_A, "ESP", "LV2");
    UUID ar = creerOption(A, DIRECTEUR_A, "AR", "LV2");
    creerOption(A, DIRECTEUR_A, "ALL", "LV2");

    Reponse r =
        put(
            SECRETARIAT_A,
            A,
            "/api/v1/classes/" + classe + "/options",
            0,
            corps("optionIds", Set.of(esp, ar)));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(200);
    assertThat(codes(r)).containsExactly("AR", "ESP");
    assertThat(codes(get(DIRECTEUR_A, A, "/api/v1/classes/" + classe + "/options")))
        .containsExactly("AR", "ESP");

    // Retrait d'une option (personne ne l'a encore choisie).
    r =
        put(
            SECRETARIAT_A,
            A,
            "/api/v1/classes/" + classe + "/options",
            0,
            corps("optionIds", Set.of(esp)));
    assertThat(codes(r)).containsExactly("ESP");

    // Une option proposée par une classe ne se supprime pas et ne change pas de groupe.
    assertThat(delete(DIRECTEUR_A, A, OPTIONS + "/" + esp).code()).isEqualTo("OPTION_UTILISEE");
    Reponse groupe =
        put(
            DIRECTEUR_A,
            A,
            OPTIONS + "/" + esp,
            0,
            corps("code", "ESP", "libelle", "Espagnol", "groupe", "ARTS"));
    assertThat(groupe.code()).isEqualTo("OPTION_UTILISEE");
  }

  @Test
  void uneOptionInactiveNePeutPasEtreAjoutee() throws Exception {
    UUID esp = creerOption(A, DIRECTEUR_A, "ESP", "LV2");
    put(
        DIRECTEUR_A,
        A,
        OPTIONS + "/" + esp,
        0,
        corps("code", "ESP", "libelle", "Espagnol", "groupe", "LV2", "actif", false));
    Reponse r =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/classes/" + classe + "/options",
            0,
            corps("optionIds", Set.of(esp)));
    assertThat(r.statut()).isEqualTo(422);
    assertThat(r.code()).isEqualTo("OPTION_INACTIVE");
  }

  @Test
  void uneOptionDUneAutreEcoleEstIntrouvable() throws Exception {
    UUID optionB = creerOption(B, DIRECTEUR_B, "ESP", "LV2");
    Reponse r =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/classes/" + classe + "/options",
            0,
            corps("optionIds", Set.of(optionB)));
    assertThat(r.statut()).isEqualTo(404);
    assertThat(r.code()).isEqualTo("OPTION_INTROUVABLE");
    assertThat(get(DIRECTEUR_A, A, OPTIONS).corps()).isEmpty();
    assertThat(delete(DIRECTEUR_A, A, OPTIONS + "/" + optionB).statut()).isEqualTo(404);
  }

  @Test
  void leReferentielDonneCapaciteCycleEtOptions() throws Exception {
    UUID esp = creerOption(A, DIRECTEUR_A, "ESP", "LV2");
    put(
        DIRECTEUR_A,
        A,
        "/api/v1/classes/" + classe + "/options",
        0,
        corps("optionIds", Set.of(esp)));

    var lue = commeRole("ROLE_SECRETARIAT", () -> referentiel.classe(classe));
    assertThat(lue.capacite()).isEqualTo(40);
    assertThat(lue.codeCycle()).isEqualTo(CodeCycle.SECONDAIRE);
    assertThat(lue.options())
        .singleElement()
        .satisfies(
            o -> {
              assertThat(o.code()).isEqualTo("ESP");
              assertThat(o.groupe()).isEqualTo("LV2");
            });
    assertThat(commeRole("ROLE_SECRETARIAT", () -> referentiel.classes(annee)))
        .extracting(ReferentielScolaire.ClasseScolaire::nom)
        .containsExactly("2NDE S1");
    var infos = commeRole("ROLE_SECRETARIAT", () -> referentiel.annee(annee));
    assertThat(infos.enCours()).isTrue();
    assertThat(infos.cloturee()).isFalse();
  }

  @Test
  void unParentNeLitQueLeNomDesClasses() throws Exception {
    assertThat(commeRole("ROLE_PARENT", () -> referentiel.nomsClasses(List.of(classe))))
        .containsEntry(classe, "2NDE S1");
    assertThat(get(PARENT_A, A, "/api/v1/classes/" + classe + "/options").statut()).isEqualTo(403);
  }
}
