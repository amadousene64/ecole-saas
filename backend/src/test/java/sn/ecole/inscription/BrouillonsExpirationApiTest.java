package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.SECRETARIAT_A;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import sn.ecole.inscription.application.PurgeBrouillons;
import sn.ecole.inscription.domain.BrouillonInscription;
import sn.ecole.shared.application.BilanExecution;

/**
 * Durée de vie des brouillons d'inscription : purge quotidienne après 30 jours sans modification,
 * plafond de brouillons en cours par auteur, liste paginée (aucun brouillon invisible).
 */
class BrouillonsExpirationApiTest extends TestInscription {

  private static final String BROUILLONS = "/api/v1/brouillons-inscription";

  @Autowired PurgeBrouillons purge;

  @BeforeEach
  void annees() throws Exception {
    creerEtOuvrirAnnee(DIRECTEUR_A, A);
  }

  private UUID ouvrir(UUID qui, UUID ecole) throws Exception {
    Reponse r = post(qui, ecole, BROUILLONS, corps());
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  private void deposerPiece(UUID id) throws Exception {
    Reponse r =
        executer(
            MockMvcRequestBuilders.multipart(HttpMethod.POST, BROUILLONS + "/" + id + "/pieces")
                .file(
                    new MockMultipartFile(
                        "fichier",
                        "acte.pdf",
                        "application/pdf",
                        "%PDF-1.4\n% acte fictif\n%%EOF".getBytes(StandardCharsets.US_ASCII)))
                .file(new MockMultipartFile("type", "", "text/plain", "ACTE_NAISSANCE".getBytes())),
            SECRETARIAT_A,
            A);
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
  }

  /** Recule la dernière modification du brouillon de {@code jours} jours. */
  private void vieillir(UUID id, int jours) {
    superUtilisateur()
        .update(
            "UPDATE ecole.brouillon_inscription SET modifie_le = now() - make_interval(days => ?)"
                + " WHERE id = ?",
            jours,
            id);
  }

  private boolean clos(UUID id) {
    return Boolean.TRUE.equals(
        superUtilisateur()
            .queryForObject(
                "SELECT supprime_le IS NOT NULL AND contenu IS NULL"
                    + " FROM ecole.brouillon_inscription WHERE id = ?",
                Boolean.class,
                id));
  }

  @Test
  void laPurgeSupprimeLesBrouillonsDePlusDe30JoursDansChaqueEcoleAvecLeursPieces()
      throws Exception {
    UUID ancien = ouvrir(SECRETARIAT_A, A);
    deposerPiece(ancien);
    UUID recent = ouvrir(SECRETARIAT_A, A);
    UUID presque = ouvrir(SECRETARIAT_A, A);
    creerEtOuvrirAnnee(DIRECTEUR_B, B);
    UUID ancienB = ouvrir(DIRECTEUR_B, B);
    vieillir(ancien, 31);
    vieillir(presque, 29);
    vieillir(ancienB, 40);
    assertThat(stockage.objets()).hasSize(1);

    BilanExecution bilan = purge.purger(Instant.now());

    assertThat(bilan.toutReussi()).isTrue();
    assertThat(clos(ancien)).isTrue();
    assertThat(clos(ancienB)).isTrue();
    assertThat(clos(recent)).isFalse();
    assertThat(clos(presque)).isFalse();
    // Pièce marquée supprimée, fichier retiré du stockage après validation.
    assertThat(
            superUtilisateur()
                .queryForObject(
                    "SELECT count(*) FROM ecole.piece_dossier"
                        + " WHERE brouillon_id = ? AND supprime_le IS NULL",
                    Integer.class,
                    ancien))
        .isZero();
    assertThat(stockage.objets()).isEmpty();
    assertThat(get(SECRETARIAT_A, A, BROUILLONS + "/" + ancien).statut()).isEqualTo(404);

    // Une trace par école concernée : le nombre seul, sans nom ni identifiant de brouillon.
    List<Map<String, Object>> traces = journal("BROUILLONS_EXPIRES");
    assertThat(traces).hasSize(2);
    assertThat(traces).extracting(t -> t.get("tenant_id")).containsExactlyInAnyOrder(A, B);
    for (Map<String, Object> trace : traces) {
      assertThat(trace.get("details").toString())
          .contains("\"nombre\": 1")
          .doesNotContain(ancien.toString())
          .doesNotContain(ancienB.toString());
    }

    // Seconde passe : plus rien à supprimer, aucune nouvelle trace.
    purge.purger(Instant.now());
    assertThat(journal("BROUILLONS_EXPIRES")).hasSize(2);
  }

  @Test
  void laPurgeVautAussiPourUneEcoleSuspendue() throws Exception {
    creerEtOuvrirAnnee(DIRECTEUR_B, B);
    UUID ancienB = ouvrir(DIRECTEUR_B, B);
    vieillir(ancienB, 31);
    superUtilisateur().update("UPDATE ecole.tenant SET actif = false WHERE id = ?", B);
    try {
      assertThat(purge.purger(Instant.now()).reussies()).contains(B);
      assertThat(clos(ancienB)).isTrue();
    } finally {
      superUtilisateur().update("UPDATE ecole.tenant SET actif = true WHERE id = ?", B);
    }
  }

  @Test
  void desOuverturesSimultaneesNeDepassentPasLePlafond() throws Exception {
    for (int i = 0; i < BrouillonInscription.MAX_PAR_AUTEUR - 1; i++) {
      ouvrir(SECRETARIAT_A, A);
    }
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<Reponse>> envois = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        envois.add(pool.submit(() -> post(SECRETARIAT_A, A, BROUILLONS, corps())));
      }
      int acceptees = 0;
      for (Future<Reponse> envoi : envois) {
        Reponse r = envoi.get();
        if (r.statut() == 201) {
          acceptees++;
        } else {
          assertThat(r.code()).isEqualTo("BROUILLONS_TROP_NOMBREUX");
        }
      }
      assertThat(acceptees).isEqualTo(1);
    } finally {
      pool.shutdown();
    }
  }

  @Test
  void laDateDeSuppressionSuitLaDerniereModification() throws Exception {
    UUID id = ouvrir(SECRETARIAT_A, A);
    Reponse lu = get(SECRETARIAT_A, A, BROUILLONS + "/" + id);
    Instant modifie = Instant.parse(lu.corps().path("modifieLe").asText());
    Instant expire = Instant.parse(lu.corps().path("expireLe").asText());
    assertThat(Duration.between(modifie, expire)).isEqualTo(Duration.ofDays(30));
    assertThat(
            get(SECRETARIAT_A, A, BROUILLONS)
                .corps()
                .path("elements")
                .get(0)
                .path("expireLe")
                .asText())
        .isEqualTo(lu.corps().path("expireLe").asText());
  }

  @Test
  void auDelaDuPlafondUnAuteurDoitTerminerOuAbandonnerUnBrouillon() throws Exception {
    List<UUID> ouverts = new ArrayList<>();
    for (int i = 0; i < BrouillonInscription.MAX_PAR_AUTEUR; i++) {
      ouverts.add(ouvrir(SECRETARIAT_A, A));
    }
    Reponse refus = post(SECRETARIAT_A, A, BROUILLONS, corps());
    assertThat(refus.statut()).isEqualTo(409);
    assertThat(refus.code()).isEqualTo("BROUILLONS_TROP_NOMBREUX");
    assertThat(refus.corps().path("detail").asText())
        .contains(BrouillonInscription.MAX_PAR_AUTEUR + " inscriptions en cours")
        .contains("abandonnez");

    // Le plafond est par auteur : un collègue peut encore commencer une inscription.
    ouvrir(DIRECTEUR_A, A);

    // Un abandon libère une place.
    UUID premier = ouverts.getFirst();
    Reponse lu = get(SECRETARIAT_A, A, BROUILLONS + "/" + premier);
    assertThat(
            executer(
                    MockMvcRequestBuilders.delete(BROUILLONS + "/" + premier)
                        .header("If-Match", lu.etag()),
                    SECRETARIAT_A,
                    A)
                .statut())
        .isEqualTo(204);
    ouvrir(SECRETARIAT_A, A);
  }

  @Test
  void laListeEstPagineeEtAucunBrouillonNeDevientInvisible() throws Exception {
    Set<UUID> ouverts = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      ouverts.add(ouvrir(SECRETARIAT_A, A));
    }
    ouverts.add(ouvrir(DIRECTEUR_A, A));

    Set<UUID> vus = new HashSet<>();
    String curseur = null;
    int pages = 0;
    do {
      Reponse page =
          get(
              SECRETARIAT_A,
              A,
              BROUILLONS + "?taille=4" + (curseur == null ? "" : "&curseur=" + curseur));
      assertThat(page.statut()).isEqualTo(200);
      page.corps()
          .path("elements")
          .forEach(b -> assertThat(vus.add(UUID.fromString(b.path("id").asText()))).isTrue());
      curseur =
          page.corps().path("curseurSuivant").isNull()
              ? null
              : page.corps().path("curseurSuivant").asText(null);
      pages++;
    } while (curseur != null && pages < 10);

    assertThat(pages).isEqualTo(2);
    assertThat(vus).isEqualTo(ouverts);
    assertThat(get(SECRETARIAT_A, A, BROUILLONS + "?curseur=abc").code())
        .isEqualTo("CURSEUR_INVALIDE");
  }

  @Test
  void laListePagineeResteDansLEcole() throws Exception {
    ouvrir(SECRETARIAT_A, A);
    creerEtOuvrirAnnee(DIRECTEUR_B, B);
    assertThat(get(DIRECTEUR_B, B, BROUILLONS).corps().path("elements")).isEmpty();
  }
}
