package sn.ecole.etablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.ECOLE_INACTIVE;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import sn.ecole.etablissement.ReferentielScolaire.AnneeEnCours;
import sn.ecole.etablissement.ReferentielScolaire.PeriodeScolaire;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/** Initialisation d'une école (modèle sénégalais) et API de lecture pour les autres modules. */
class InitialisationEcoleTest extends TestEtablissement {

  @Autowired ExecutionPourTenant execution;
  @Autowired ReferentielScolaire referentiel;

  @Test
  void uneEcoleInitialiseeADesCyclesNiveauxEtSeries() {
    assertThat(initialisation.estInitialisee(A)).isTrue();
    assertThat(compter("cycle", A)).isEqualTo(3);
    assertThat(compter("niveau", A)).isEqualTo(13);
    assertThat(compter("serie", A)).isEqualTo(7);
    assertThat(compter("niveau_serie", A)).isEqualTo(2 + 5 + 5);
    assertThat(compter("etablissement", A)).isEqualTo(1);
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT nom || '/' || code || '/' || statut || '/' || fuseau || '/' || devise FROM ecole.etablissement WHERE tenant_id = ?",
                    String.class,
                    A))
        .isEqualTo("Lycée Exemple A/LYC-A/PUBLIC/Africa/Dakar/XOF");
  }

  @Test
  void lInitialisationEstRefuseeDeuxFois() {
    assertThatThrownBy(
            () -> initialisation.initialiser(A, "Autre", "AUTRE", StatutEtablissement.PUBLIC))
        .isInstanceOfSatisfying(
            ErreurMetier.class,
            e -> {
              assertThat(e.code()).isEqualTo("ETABLISSEMENT_EXISTANT");
              assertThat(e.type()).isEqualTo(TypeErreur.CONFLIT);
            });
    assertThat(compter("cycle", A)).isEqualTo(3);
  }

  @Test
  void uneEcoleSansProfilNEstPasInitialisee() {
    assertThat(initialisation.estInitialisee(ECOLE_INACTIVE)).isFalse();
  }

  @Test
  void chaqueEcoleAUneCopiePropreDuModele() throws Exception {
    // Modifier la copie de A ne touche ni le modèle ni la copie de B.
    UUID cycleA = cycle(DIRECTEUR_A, A, "MOYEN");
    var cycles = get(DIRECTEUR_A, A, "/api/v1/cycles").corps();
    int version = cycles.get(1).path("version").asInt();
    put(
        DIRECTEUR_A,
        A,
        "/api/v1/cycles/" + cycleA,
        version,
        corps(
            "libelle",
            "Moyen A",
            "typePeriode",
            "TRIMESTRE",
            "modeNotation",
            "COEFFICIENT",
            "baseMoyenne",
            20));
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT type_periode FROM ecole.cycle WHERE tenant_id = ? AND code = 'MOYEN'",
                    String.class,
                    B))
        .isEqualTo("SEMESTRE");
    assertThat(sn.ecole.etablissement.domain.ModeleSenegalais.CYCLES.get(1).typePeriode().name())
        .isEqualTo("SEMESTRE");
  }

  // --- API de lecture pour les autres modules ---

  private <T> T commeDirecteur(java.util.function.Supplier<T> action) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                DIRECTEUR_A.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTEUR"))));
    try {
      return execution.lire(A, action);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void leReferentielRenvoieLAnneeEnCoursEtSesPeriodes() throws Exception {
    assertThat(commeDirecteur(() -> referentiel.anneeEnCours())).isEmpty();

    UUID annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    AnneeEnCours courante = commeDirecteur(() -> referentiel.anneeEnCours()).orElseThrow();
    assertThat(courante.id()).isEqualTo(annee);
    assertThat(courante.libelle()).isEqualTo("2026-2027");

    List<PeriodeScolaire> periodes = commeDirecteur(() -> referentiel.periodes(annee));
    assertThat(periodes).hasSize(7);
    assertThat(periodes)
        .extracting(PeriodeScolaire::libelle)
        .contains("1er trimestre", "2e semestre");
  }

  @Test
  void leReferentielExigeUnRoleDeLEcole() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_AUTRE"))));
    try {
      assertThatThrownBy(() -> execution.lire(A, () -> referentiel.anneeEnCours()))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private int compter(String table, UUID ecole) {
    return superUtilisateur()
        .queryForObject(
            "SELECT count(*) FROM ecole." + table + " WHERE tenant_id = ?", Integer.class, ecole);
  }
}
