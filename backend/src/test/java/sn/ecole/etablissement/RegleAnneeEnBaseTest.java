package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.DIRECTEUR_A;

import java.sql.PreparedStatement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;

/**
 * Les règles d'année tiennent aussi dans PostgreSQL, indépendamment du code Java : une seule année
 * en cours par école, année clôturée en lecture seule.
 */
class RegleAnneeEnBaseTest extends TestEtablissement {

  private static int executer(UUID ecole, String sql, Object... parametres) {
    return roleApplicatif()
        .execute(
            (ConnectionCallback<Integer>)
                connexion -> {
                  try (PreparedStatement ps =
                      connexion.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                    ps.setString(1, ecole.toString());
                    ps.execute();
                  }
                  try (PreparedStatement ps = connexion.prepareStatement(sql)) {
                    for (int i = 0; i < parametres.length; i++) {
                      ps.setObject(i + 1, parametres[i]);
                    }
                    return ps.executeUpdate();
                  }
                });
  }

  @Test
  void uneDeuxiemeAnneeEnCoursEstRefuseeParLaBase() throws Exception {
    creerEtOuvrirAnnee(DIRECTEUR_A, A);
    assertThatThrownBy(
            () ->
                executer(
                    A,
                    "INSERT INTO annee_scolaire (id, tenant_id, libelle, date_debut, date_fin, statut)"
                        + " VALUES (gen_random_uuid(), ?, '2030-2031', '2030-10-01', '2031-07-01', 'EN_COURS')",
                    A))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("uq_annee_scolaire_en_cours");
  }

  @Test
  void lIndexNeCompteQueLesAnneesNonSupprimeesEtUneParEcole() throws Exception {
    creerEtOuvrirAnnee(DIRECTEUR_A, A);
    // L'école B a sa propre année en cours, et une année en préparation ne gêne personne.
    assertThat(
            executer(
                B,
                "INSERT INTO annee_scolaire (id, tenant_id, libelle, date_debut, date_fin, statut)"
                    + " VALUES (gen_random_uuid(), ?, '2026-2027', '2026-10-01', '2027-07-01', 'EN_COURS')",
                B))
        .isEqualTo(1);
    assertThat(
            executer(
                A,
                "INSERT INTO annee_scolaire (id, tenant_id, libelle, date_debut, date_fin, statut)"
                    + " VALUES (gen_random_uuid(), ?, '2031-2032', '2031-10-01', '2032-07-01', 'PREPARATION')",
                A))
        .isEqualTo(1);
  }

  @Test
  void uneAnneeClotureeNeSeModifiePlusEnBase() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    UUID classe =
        post(
                DIRECTEUR_A,
                A,
                "/api/v1/classes",
                corps(
                    "anneeId",
                    annee,
                    "niveauId",
                    niveau(DIRECTEUR_A, A, "CP"),
                    "nom",
                    "CPA",
                    "capacite",
                    30))
            .id();
    post(DIRECTEUR_A, A, "/api/v1/annees-scolaires/" + annee + "/cloture", null);

    assertThatThrownBy(
            () ->
                executer(A, "UPDATE annee_scolaire SET libelle = '2026-2027' WHERE id = ?", annee))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ANNEE_CLOTUREE");
    assertThatThrownBy(
            () -> executer(A, "UPDATE periode SET libelle = 'x' WHERE annee_id = ?", annee))
        .hasMessageContaining("ANNEE_CLOTUREE");
    assertThatThrownBy(
            () ->
                executer(
                    A, "UPDATE evenement_calendrier SET libelle = 'x' WHERE annee_id = ?", annee))
        .hasMessageContaining("ANNEE_CLOTUREE");
    assertThatThrownBy(() -> executer(A, "UPDATE classe SET nom = 'x' WHERE id = ?", classe))
        .hasMessageContaining("ANNEE_CLOTUREE");
    // Y compris la suppression logique.
    assertThatThrownBy(
            () -> executer(A, "UPDATE classe SET supprime_le = now() WHERE id = ?", classe))
        .hasMessageContaining("ANNEE_CLOTUREE");
    assertThatThrownBy(
            () ->
                executer(
                    A,
                    "INSERT INTO classe (id, tenant_id, annee_id, niveau_id, nom, capacite)"
                        + " SELECT gen_random_uuid(), tenant_id, annee_id, niveau_id, 'NOUVELLE', 30 FROM classe WHERE id = ?",
                    classe))
        .hasMessageContaining("ANNEE_CLOTUREE");

    assertThat(
            superUtilisateur()
                .queryForObject("SELECT nom FROM ecole.classe WHERE id = ?", String.class, classe))
        .isEqualTo("CPA");
  }

  @Test
  void uneAnneeEnCoursResteModifiableEnBase() throws Exception {
    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    assertThat(executer(A, "UPDATE periode SET libelle = 'Trimestre 1' WHERE annee_id = ?", annee))
        .isPositive();
  }

  @Test
  void leStatutEtLesDatesSontContraintsParLaBase() throws Exception {
    assertThatThrownBy(
            () ->
                executer(
                    A,
                    "INSERT INTO annee_scolaire (id, tenant_id, libelle, date_debut, date_fin, statut)"
                        + " VALUES (gen_random_uuid(), ?, '2040-2041', '2041-07-01', '2040-10-01', 'PREPARATION')",
                    A))
        .hasMessageContaining("ck_annee_scolaire_dates");
    assertThatThrownBy(
            () ->
                executer(
                    A,
                    "INSERT INTO annee_scolaire (id, tenant_id, libelle, date_debut, date_fin, statut)"
                        + " VALUES (gen_random_uuid(), ?, '2040-2041', '2040-10-01', '2041-07-01', 'ARCHIVEE')",
                    A))
        .hasMessageContaining("ck_annee_scolaire_statut");
  }
}
