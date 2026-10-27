package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import sn.ecole.etablissement.ReferentielScolaire.ParametresCycle;
import sn.ecole.etablissement.ReferentielScolaire.ParametresNiveau;
import sn.ecole.etablissement.ReferentielScolaire.ParametresNotation;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.ErreurMetier;
import tools.jackson.databind.JsonNode;

/**
 * Les paramètres de notation sont figés par année à son ouverture : le modèle des cycles et des
 * niveaux reste modifiable, mais une année ouverte ou clôturée garde les siens.
 */
class ParametresAnneeTest extends TestEtablissement {

  private static final String ANNEES = "/api/v1/annees-scolaires/";

  @Autowired ExecutionPourTenant execution;
  @Autowired ReferentielScolaire referentiel;

  private <T> T lireCommeDirecteur(UUID ecole, String role, Supplier<T> action) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                DIRECTEUR_A.toString(), "n/a", List.of(new SimpleGrantedAuthority(role))));
    try {
      return execution.lire(ecole, action);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private ParametresNotation parametres(UUID annee) {
    return lireCommeDirecteur(A, "ROLE_DIRECTEUR", () -> referentiel.parametresNotation(annee));
  }

  private static ParametresCycle cycle(ParametresNotation p, CodeCycle code) {
    return p.cycles().stream().filter(c -> c.code() == code).findFirst().orElseThrow();
  }

  private ParametresNiveau niveau(ParametresNotation p, UUID niveauId) {
    return p.niveaux().stream()
        .filter(n -> n.niveauId().equals(niveauId))
        .findFirst()
        .orElseThrow();
  }

  private void modifierCycle(String code, String type, String mode, int base) throws Exception {
    JsonNode cycle = null;
    for (JsonNode c : get(DIRECTEUR_A, A, "/api/v1/cycles").corps()) {
      if (code.equals(c.path("code").asText())) {
        cycle = c;
      }
    }
    Reponse r =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/cycles/" + cycle.path("id").asText(),
            cycle.path("version").asInt(),
            corps("libelle", code, "typePeriode", type, "modeNotation", mode, "baseMoyenne", base));
    assertThat(r.statut()).as("modification du cycle %s", code).isEqualTo(200);
  }

  private void surchargerBaseDuNiveau(String code, int base) throws Exception {
    JsonNode n = null;
    for (JsonNode x : get(DIRECTEUR_A, A, "/api/v1/niveaux").corps()) {
      if (code.equals(x.path("code").asText())) {
        n = x;
      }
    }
    Reponse r =
        put(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux/" + n.path("id").asText(),
            n.path("version").asInt(),
            corps(
                "libelle",
                n.path("libelle").asText(),
                "ordre",
                n.path("ordre").asInt(),
                "baseMoyenne",
                base,
                "actif",
                true));
    assertThat(r.statut()).as("surcharge du niveau %s", code).isEqualTo(200);
  }

  private int compter(String table, UUID annee) {
    return superUtilisateur()
        .queryForObject(
            "SELECT count(*) FROM ecole." + table + " WHERE annee_id = ?", Integer.class, annee);
  }

  // --- Figeage à l'ouverture ---

  @Test
  void lOuvertureCopieLesParametresDeChaqueCycleEtNiveau() throws Exception {
    UUID annee = creerAnnee(DIRECTEUR_A, A);
    assertThat(compter("parametres_cycle_annee", annee)).isZero();
    assertThat(compter("parametres_niveau_annee", annee)).isZero();

    post(DIRECTEUR_A, A, ANNEES + annee + "/ouverture", null);

    assertThat(compter("parametres_cycle_annee", annee)).isEqualTo(3);
    assertThat(compter("parametres_niveau_annee", annee)).isEqualTo(13);
    ParametresNotation p = parametres(annee);
    assertThat(p.figes()).isTrue();
    assertThat(cycle(p, CodeCycle.ELEMENTAIRE).typePeriode()).isEqualTo(TypePeriode.TRIMESTRE);
    assertThat(cycle(p, CodeCycle.ELEMENTAIRE).modeNotation()).isEqualTo(ModeNotation.BAREME);
    assertThat(cycle(p, CodeCycle.ELEMENTAIRE).baseMoyenne()).isEqualTo(10);
    assertThat(cycle(p, CodeCycle.SECONDAIRE).typePeriode()).isEqualTo(TypePeriode.SEMESTRE);
    assertThat(cycle(p, CodeCycle.SECONDAIRE).baseMoyenne()).isEqualTo(20);
    assertThat(niveau(p, niveau(DIRECTEUR_A, A, "CP")).baseMoyenne()).isEqualTo(10);
    assertThat(niveau(p, niveau(DIRECTEUR_A, A, "6E")).baseMoyenne()).isEqualTo(20);
    assertThat(p.niveaux()).hasSize(13);
  }

  @Test
  void lesModificationsDuModeleNAffectentPasUneAnneeOuverte() throws Exception {
    UUID cp = niveau(DIRECTEUR_A, A, "CP");
    UUID ouverte = creerEtOuvrirAnnee(DIRECTEUR_A, A);

    modifierCycle("MOYEN", "TRIMESTRE", "COEFFICIENT", 20);
    surchargerBaseDuNiveau("CP", 20);

    ParametresNotation figee = parametres(ouverte);
    assertThat(figee.figes()).isTrue();
    assertThat(cycle(figee, CodeCycle.MOYEN).typePeriode()).isEqualTo(TypePeriode.SEMESTRE);
    assertThat(niveau(figee, cp).baseMoyenne()).isEqualTo(10);
  }

  @Test
  void uneAnneeEnPreparationSuitLeModeleEtSeFigeALOuverture() throws Exception {
    UUID cp = niveau(DIRECTEUR_A, A, "CP");
    UUID premiere = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    Reponse suivante =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/annees-scolaires",
            corps("libelle", "2027-2028", "dateDebut", "2027-10-04", "dateFin", "2028-07-07"));

    modifierCycle("MOYEN", "TRIMESTRE", "COEFFICIENT", 20);
    surchargerBaseDuNiveau("CP", 20);

    // En préparation : le modèle d'aujourd'hui, marqué non figé.
    ParametresNotation vivante = parametres(suivante.id());
    assertThat(vivante.figes()).isFalse();
    assertThat(cycle(vivante, CodeCycle.MOYEN).typePeriode()).isEqualTo(TypePeriode.TRIMESTRE);
    assertThat(niveau(vivante, cp).baseMoyenne()).isEqualTo(20);

    // Les périodes générées avant le changement ne suffisent plus : on les régénère, puis on ouvre.
    post(DIRECTEUR_A, A, ANNEES + premiere + "/cloture", null);
    assertThat(post(DIRECTEUR_A, A, ANNEES + suivante.id() + "/periodes/generation", null).statut())
        .isEqualTo(200);
    assertThat(post(DIRECTEUR_A, A, ANNEES + suivante.id() + "/ouverture", null).statut())
        .isEqualTo(200);

    ParametresNotation nouvelle = parametres(suivante.id());
    assertThat(nouvelle.figes()).isTrue();
    assertThat(cycle(nouvelle, CodeCycle.MOYEN).typePeriode()).isEqualTo(TypePeriode.TRIMESTRE);
    assertThat(niveau(nouvelle, cp).baseMoyenne()).isEqualTo(20);
    // L'année précédente, clôturée, a gardé les siens.
    ParametresNotation ancienne = parametres(premiere);
    assertThat(cycle(ancienne, CodeCycle.MOYEN).typePeriode()).isEqualTo(TypePeriode.SEMESTRE);
    assertThat(niveau(ancienne, cp).baseMoyenne()).isEqualTo(10);
  }

  @Test
  void uneAnneeClotureeGardeSesParametres() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    ParametresNotation avant = parametres(annee);
    post(DIRECTEUR_A, A, ANNEES + annee + "/cloture", null);
    modifierCycle("ELEMENTAIRE", "TRIMESTRE", "BAREME", 20);

    ParametresNotation apres = parametres(annee);
    assertThat(apres.figes()).isTrue();
    assertThat(apres.cycles()).isEqualTo(avant.cycles());
    assertThat(apres.niveaux()).isEqualTo(avant.niveaux());
    assertThat(cycle(apres, CodeCycle.ELEMENTAIRE).baseMoyenne()).isEqualTo(10);
  }

  @Test
  void unNiveauCreeEnCoursDAnneeSeFigeToutDeSuiteAvecLesParametresFigesDuCycle() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    // Le modèle du cycle change après l'ouverture : la copie figée de l'année, elle, ne bouge pas.
    modifierCycle("ELEMENTAIRE", "TRIMESTRE", "BAREME", 20);

    Reponse r =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux",
            corps(
                "cycleId",
                cycle(DIRECTEUR_A, A, "ELEMENTAIRE"),
                "code",
                "CM3",
                "libelle",
                "CM3",
                "ordre",
                20));
    assertThat(r.statut()).isEqualTo(201);

    ParametresNotation p = parametres(annee);
    assertThat(p.niveaux()).hasSize(14);
    assertThat(niveau(p, r.id()).baseMoyenne()).isEqualTo(10); // base figée du cycle, pas 20
    assertThat(compter("parametres_niveau_annee", annee)).isEqualTo(14);

    // Surcharge à la création : retenue, mais refusée si le cycle figé est par coefficients.
    Reponse surcharge =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/niveaux",
            corps(
                "cycleId",
                cycle(DIRECTEUR_A, A, "ELEMENTAIRE"),
                "code",
                "CM4",
                "libelle",
                "CM4",
                "ordre",
                21,
                "baseMoyenne",
                20));
    assertThat(surcharge.statut()).isEqualTo(201);
    assertThat(niveau(parametres(annee), surcharge.id()).baseMoyenne()).isEqualTo(20);
  }

  @Test
  void unNiveauCreeEnPreparationNeCreeAucuneCopie() throws Exception {
    UUID preparation = creerAnnee(DIRECTEUR_A, A);
    post(
        DIRECTEUR_A,
        A,
        "/api/v1/niveaux",
        corps(
            "cycleId", cycle(DIRECTEUR_A, A, "MOYEN"), "code", "XX", "libelle", "XX", "ordre", 30));
    assertThat(compter("parametres_niveau_annee", preparation)).isZero();
    assertThat(parametres(preparation).figes()).isFalse();
  }

  // --- API publique ---

  @Test
  void uneAnneeInconnueOuDUneAutreEcoleEstIntrouvable() throws Exception {
    UUID anneeB = creerEtOuvrirAnnee(DIRECTEUR_B, B);
    assertThatThrownBy(() -> parametres(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("ANNEE_INTROUVABLE"));
    assertThatThrownBy(() -> parametres(anneeB))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("ANNEE_INTROUVABLE"));
  }

  @Test
  void toutRoleDeLEcoleLitLesParametres_unRoleEtrangerNon() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    assertThat(
            lireCommeDirecteur(A, "ROLE_PARENT", () -> referentiel.parametresNotation(annee))
                .figes())
        .isTrue();
    assertThatThrownBy(
            () -> lireCommeDirecteur(A, "ROLE_AUTRE", () -> referentiel.parametresNotation(annee)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  // --- La base garantit l'immuabilité et la cohérence ---

  @Test
  void uneCopieFigeeNeSeModifieNiNeSeSupprimePasEnBase() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);

    assertThatThrownBy(
            () ->
                superUtilisateur()
                    .update(
                        "UPDATE ecole.parametres_cycle_annee SET base_moyenne = 10 WHERE annee_id = ?",
                        annee))
        .hasMessageContaining("PARAMETRES_FIGES");
    assertThatThrownBy(
            () ->
                superUtilisateur()
                    .update("DELETE FROM ecole.parametres_niveau_annee WHERE annee_id = ?", annee))
        .hasMessageContaining("PARAMETRES_FIGES");
    // Le rôle applicatif n'a pas même le droit d'essayer.
    assertThatThrownBy(
            () ->
                roleApplicatif()
                    .update(
                        "UPDATE parametres_cycle_annee SET base_moyenne = 10 WHERE annee_id = ?",
                        annee))
        .isInstanceOf(DataAccessException.class);
    assertThat(compter("parametres_cycle_annee", annee)).isEqualTo(3);
  }

  @Test
  void laBaseDixEstRefuseeEnCoefficientsAuNiveauDeLaBase() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    assertThatThrownBy(
            () ->
                superUtilisateur()
                    .update(
                        "UPDATE ecole.cycle SET base_moyenne = 10 WHERE tenant_id = ? AND code = 'MOYEN'",
                        A))
        .hasMessageContaining("ck_cycle_base_coefficient");
    UUID cycle =
        superUtilisateur()
            .queryForObject(
                "SELECT id FROM ecole.cycle WHERE tenant_id = ? AND code = 'MOYEN'", UUID.class, A);
    UUID niveau = niveau(DIRECTEUR_A, A, "6E");
    assertThatThrownBy(
            () ->
                superUtilisateur()
                    .update(
                        "INSERT INTO ecole.parametres_niveau_annee (id, tenant_id, annee_id, niveau_id, cycle_id, mode_notation, base_moyenne)"
                            + " VALUES (gen_random_uuid(), ?, ?, ?, ?, 'COEFFICIENT', 10)",
                        A,
                        annee,
                        niveau,
                        cycle))
        .hasMessageContaining("ck_parametres_niveau_annee_coefficient");
  }
}
