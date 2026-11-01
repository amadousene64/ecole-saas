package sn.ecole.inscription;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.ecole.Fixtures.COMPTABLE_A;
import static sn.ecole.Fixtures.DIRECTEUR_A;
import static sn.ecole.Fixtures.DIRECTEUR_B;
import static sn.ecole.Fixtures.ELEVE_A;
import static sn.ecole.Fixtures.SECRETARIAT_A;
import static sn.ecole.Fixtures.SURVEILLANT_A;

import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tools.jackson.databind.JsonNode;

/** Inscriptions : unicité par année, capacité, options, statuts, droits, verrou de classe. */
@RecordApplicationEvents
class InscriptionsApiTest extends TestInscription {

  private static final String INSCRIPTIONS = "/api/v1/inscriptions";

  @Autowired ApplicationEvents evenements;

  private UUID annee;
  private UUID sixieme;
  private UUID ci;
  private UUID awa;

  @BeforeEach
  void structure() throws Exception {
    annee = creerEtOuvrirAnnee(DIRECTEUR_A, A);
    sixieme = creerClasse(A, DIRECTEUR_A, annee, "6E", "6E A", 2);
    ci = creerClasse(A, DIRECTEUR_A, annee, "CI", "CI A", 30);
    awa = creerEleve(A, "Awa");
  }

  private UUID creerClasse(UUID ecole, UUID qui, UUID an, String niveau, String nom, int capacite)
      throws Exception {
    Reponse r =
        post(
            qui,
            ecole,
            "/api/v1/classes",
            corps(
                "anneeId",
                an,
                "niveauId",
                niveau(qui, ecole, niveau),
                "nom",
                nom,
                "capacite",
                capacite));
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  private Reponse inscrire(UUID qui, UUID eleve, UUID classe, String statut, List<UUID> options)
      throws Exception {
    return post(
        qui,
        A,
        INSCRIPTIONS,
        corps(
            "eleveId",
            eleve,
            "classeId",
            classe,
            "statut",
            statut,
            "redoublant",
            false,
            "optionIds",
            options));
  }

  private UUID inscrit(UUID eleve, UUID classe) throws Exception {
    Reponse r = inscrire(SECRETARIAT_A, eleve, classe, "INSCRIT", List.of());
    assertThat(r.statut()).as(r.corps().toString()).isEqualTo(201);
    return r.id();
  }

  private void politique(String politique) throws Exception {
    Reponse p = get(DIRECTEUR_A, A, "/api/v1/parametres-inscription");
    assertThat(
            put(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/parametres-inscription",
                    versionEtag(p),
                    corps("formatMatricule", "{AAAA}{SEQ:5}", "politiqueCapacite", politique))
                .statut())
        .isEqualTo(200);
  }

  @Test
  void preInscriptionPuisValidationAvecEvenement() throws Exception {
    Reponse r = inscrire(SECRETARIAT_A, awa, sixieme, "PRE_INSCRIT", List.of());
    assertThat(r.statut()).isEqualTo(201);
    assertThat(r.corps().path("classe").asText()).isEqualTo("6E A");
    assertThat(r.corps().path("anneeId").asText()).isEqualTo(annee.toString());
    assertThat(evenements.stream(InscriptionValidee.class)).isEmpty();

    Reponse v = post(SECRETARIAT_A, A, INSCRIPTIONS + "/" + r.id() + "/validation", null);
    assertThat(v.statut()).isEqualTo(400); // If-Match obligatoire
    v =
        executer(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    INSCRIPTIONS + "/" + r.id() + "/validation")
                .header("If-Match", r.etag()),
            SECRETARIAT_A,
            A);
    assertThat(v.statut()).as(v.corps().toString()).isEqualTo(200);
    assertThat(v.corps().path("statut").asText()).isEqualTo("INSCRIT");
    assertThat(evenements.stream(InscriptionValidee.class))
        .singleElement()
        .satisfies(e -> assertThat(e.eleveId()).isEqualTo(awa));
  }

  @Test
  void uneSeuleInscriptionActiveParAnneeMaisReinscriptionApresSortie() throws Exception {
    UUID premiere = inscrit(awa, sixieme);
    assertThat(inscrire(SECRETARIAT_A, awa, ci, "INSCRIT", List.of()).code())
        .isEqualTo("INSCRIPTION_ACTIVE_EXISTANTE");

    Reponse lue = get(SECRETARIAT_A, A, INSCRIPTIONS + "/" + premiere);
    Reponse sortie =
        executer(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    INSCRIPTIONS + "/" + premiere + "/sortie")
                .header("If-Match", lue.etag())
                .contentType("application/json")
                .content(
                    JSON.writeValueAsString(
                        Map.of(
                            "statut",
                            "TRANSFERE",
                            "date",
                            LocalDate.now().toString(),
                            "motif",
                            "Déménagement"))),
            SECRETARIAT_A,
            A);
    assertThat(sortie.statut()).as(sortie.corps().toString()).isEqualTo(200);
    assertThat(evenements.stream(InscriptionTerminee.class)).hasSize(1);
    assertThat(inscrire(SECRETARIAT_A, awa, ci, "INSCRIT", List.of()).statut()).isEqualTo(201);

    Reponse historique = get(SURVEILLANT_A, A, ELEVES + "/" + awa + "/inscriptions");
    assertThat(historique.corps()).hasSize(2);
  }

  private Reponse sortir(UUID qui, UUID inscription, String statut) throws Exception {
    Reponse lue = get(DIRECTEUR_A, A, INSCRIPTIONS + "/" + inscription);
    return executer(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                INSCRIPTIONS + "/" + inscription + "/sortie")
            .header("If-Match", lue.etag())
            .contentType("application/json")
            .content(
                JSON.writeValueAsString(
                    Map.of("statut", statut, "date", LocalDate.now().toString()))),
        qui,
        A);
  }

  @Test
  void laRadiationEstReserveeAuDirecteur() throws Exception {
    UUID inscription = inscrit(awa, sixieme);
    assertThat(sortir(SECRETARIAT_A, inscription, "RADIE").statut()).isEqualTo(403);
    assertThat(sortir(DIRECTEUR_A, inscription, "RADIE").statut()).isEqualTo(200);
    UUID binta = creerEleve(A, "Binta");
    UUID autre = inscrit(binta, sixieme);
    assertThat(sortir(SECRETARIAT_A, autre, "ABANDON").statut()).isEqualTo(200);
    assertThat(journal("INSCRIPTION_TERMINEE")).hasSize(2);
  }

  @Test
  void auDelaDeLaCapaciteOnAvertitOuOnBloqueSelonLEcole() throws Exception {
    inscrit(awa, sixieme);
    inscrit(creerEleve(A, "Binta"), sixieme);
    Reponse averti =
        inscrire(SECRETARIAT_A, creerEleve(A, "Coumba"), sixieme, "INSCRIT", List.of());
    assertThat(averti.statut()).isEqualTo(201);
    assertThat(averti.corps().path("avertissements").get(0).asText())
        .startsWith("CAPACITE_DEPASSEE");

    politique("BLOQUER");
    Reponse bloque =
        inscrire(SECRETARIAT_A, creerEleve(A, "Dior"), sixieme, "PRE_INSCRIT", List.of());
    assertThat(bloque.statut()).isEqualTo(409);
    assertThat(bloque.code()).isEqualTo("CAPACITE_ATTEINTE");
  }

  @Test
  void desInscriptionsSimultaneesNeDepassentPasLaCapacite() throws Exception {
    politique("BLOQUER");
    List<UUID> candidats = new ArrayList<>();
    for (String p : List.of("Binta", "Coumba", "Dior", "Fatou", "Khady", "Mariama")) {
      candidats.add(creerEleve(A, p));
    }
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<Reponse>> envois = new ArrayList<>();
      for (UUID c : candidats) {
        envois.add(pool.submit(() -> inscrire(SECRETARIAT_A, c, sixieme, "INSCRIT", List.of())));
      }
      int acceptees = 0;
      for (Future<Reponse> envoi : envois) {
        Reponse r = envoi.get();
        if (r.statut() == 201) {
          acceptees++;
        } else {
          assertThat(r.code()).isEqualTo("CAPACITE_ATTEINTE");
        }
      }
      assertThat(acceptees).isEqualTo(2);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void lesOptionsSontCellesDeLaClasseEtUneParGroupe() throws Exception {
    UUID esp =
        post(
                DIRECTEUR_A,
                A,
                "/api/v1/options-enseignement",
                corps("code", "ESP", "libelle", "Espagnol", "groupe", "LV2"))
            .id();
    UUID ar =
        post(
                DIRECTEUR_A,
                A,
                "/api/v1/options-enseignement",
                corps("code", "AR", "libelle", "Arabe", "groupe", "LV2"))
            .id();
    UUID all =
        post(
                DIRECTEUR_A,
                A,
                "/api/v1/options-enseignement",
                corps("code", "ALL", "libelle", "Allemand", "groupe", "LV2"))
            .id();
    assertThat(
            put(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/classes/" + sixieme + "/options",
                    0,
                    corps("optionIds", Set.of(esp, ar)))
                .statut())
        .isEqualTo(200);

    assertThat(inscrire(SECRETARIAT_A, awa, sixieme, "INSCRIT", List.of(all)).code())
        .isEqualTo("OPTION_NON_PROPOSEE");
    assertThat(inscrire(SECRETARIAT_A, awa, sixieme, "INSCRIT", List.of(esp, ar)).code())
        .isEqualTo("OPTIONS_INCOMPATIBLES");
    Reponse ok = inscrire(SECRETARIAT_A, awa, sixieme, "INSCRIT", List.of(esp));
    assertThat(ok.statut()).isEqualTo(201);
    assertThat(ok.corps().path("optionIds").get(0).asText()).isEqualTo(esp.toString());

    // Une option choisie par un élève ne peut plus être retirée de la classe.
    assertThat(
            put(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/classes/" + sixieme + "/options",
                    0,
                    corps("optionIds", Set.of(ar)))
                .code())
        .isEqualTo("OPTION_UTILISEE");

    // Changement d'option.
    Reponse m =
        put(
            SECRETARIAT_A,
            A,
            INSCRIPTIONS + "/" + ok.id(),
            ok.version(),
            corps("redoublant", true, "optionIds", List.of(ar)));
    assertThat(m.statut()).as(m.corps().toString()).isEqualTo(200);
    assertThat(m.corps().path("redoublant").asBoolean()).isTrue();
    assertThat(m.corps().path("optionIds").get(0).asText()).isEqualTo(ar.toString());
  }

  @Test
  void leChangementDeClasseResteDansLAnnee() throws Exception {
    Reponse r = inscrire(SECRETARIAT_A, awa, ci, "INSCRIT", List.of());
    Reponse m =
        put(
            SECRETARIAT_A,
            A,
            INSCRIPTIONS + "/" + r.id(),
            r.version(),
            corps("classeId", sixieme, "redoublant", false, "optionIds", List.of()));
    assertThat(m.statut()).as(m.corps().toString()).isEqualTo(200);
    assertThat(m.corps().path("classe").asText()).isEqualTo("6E A");

    Reponse autreAnnee =
        post(
            DIRECTEUR_A,
            A,
            "/api/v1/annees-scolaires",
            corps("libelle", "2027-2028", "dateDebut", "2027-10-04", "dateFin", "2028-07-01"));
    UUID classeSuivante = creerClasse(A, DIRECTEUR_A, autreAnnee.id(), "5E", "5E A", 40);
    Reponse refus =
        put(
            SECRETARIAT_A,
            A,
            INSCRIPTIONS + "/" + r.id(),
            m.version(),
            corps("classeId", classeSuivante, "redoublant", false, "optionIds", List.of()));
    assertThat(refus.code()).isEqualTo("ANNEE_DIFFERENTE");
    // Mais une pré-inscription pour l'année suivante est possible.
    assertThat(inscrire(SECRETARIAT_A, awa, classeSuivante, "PRE_INSCRIT", List.of()).statut())
        .isEqualTo(201);
  }

  @Test
  void rienNeSInscritDansUneAnneeCloturee() throws Exception {
    UUID inscription = inscrit(awa, sixieme);
    assertThat(
            post(DIRECTEUR_A, A, "/api/v1/annees-scolaires/" + annee + "/cloture", null).statut())
        .isEqualTo(200);
    assertThat(inscrire(SECRETARIAT_A, creerEleve(A, "Binta"), ci, "INSCRIT", List.of()).code())
        .isEqualTo("ANNEE_CLOTUREE");
    assertThat(sortir(DIRECTEUR_A, inscription, "RADIE").code()).isEqualTo("ANNEE_CLOTUREE");
    // Et la base refuse aussi, même sans passer par le service.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                superUtilisateur()
                    .update(
                        "UPDATE ecole.inscription SET redoublant = true WHERE id = ?", inscription))
        .hasMessageContaining("ANNEE_CLOTUREE");
  }

  @Test
  void issue3UneClasseAvecDesInscritsNeSeSupprimeNiNeChangeDeNiveau() throws Exception {
    UUID annee2 =
        post(
                DIRECTEUR_A,
                A,
                "/api/v1/annees-scolaires",
                corps("libelle", "2027-2028", "dateDebut", "2027-10-04", "dateFin", "2028-07-01"))
            .id();
    UUID classe = creerClasse(A, DIRECTEUR_A, annee2, "5E", "5E B", 40);
    Reponse pre = inscrire(SECRETARIAT_A, awa, classe, "PRE_INSCRIT", List.of());
    assertThat(pre.statut()).isEqualTo(201);

    Reponse lue = get(DIRECTEUR_A, A, "/api/v1/classes/" + classe);
    Object versQuatrieme =
        corps(
            "anneeId",
            annee2,
            "niveauId",
            niveau(DIRECTEUR_A, A, "4E"),
            "nom",
            "5E B",
            "capacite",
            40);
    assertThat(
            put(DIRECTEUR_A, A, "/api/v1/classes/" + classe, lue.version(), versQuatrieme).code())
        .isEqualTo("CLASSE_UTILISEE");
    assertThat(delete(DIRECTEUR_A, A, "/api/v1/classes/" + classe).code())
        .isEqualTo("CLASSE_UTILISEE");

    // Même après abandon, la classe reste « utilisée » : pas de suppression puis recréation.
    Reponse ip = get(DIRECTEUR_A, A, INSCRIPTIONS + "/" + pre.id());
    assertThat(sortir(DIRECTEUR_A, pre.id(), "ABANDON").statut()).isEqualTo(200);
    assertThat(ip.statut()).isEqualTo(200);
    assertThat(delete(DIRECTEUR_A, A, "/api/v1/classes/" + classe).code())
        .isEqualTo("CLASSE_UTILISEE");
    assertThat(
            post(
                    DIRECTEUR_A,
                    A,
                    "/api/v1/classes",
                    corps(
                        "anneeId",
                        annee2,
                        "niveauId",
                        niveau(DIRECTEUR_A, A, "4E"),
                        "nom",
                        "5E B",
                        "capacite",
                        40))
                .code())
        .isEqualTo("CLASSE_EXISTANTE");

    // Une classe sans inscription se supprime toujours.
    UUID vide = creerClasse(A, DIRECTEUR_A, annee2, "5E", "5E C", 40);
    assertThat(delete(DIRECTEUR_A, A, "/api/v1/classes/" + vide).statut()).isEqualTo(204);
  }

  @Test
  void laListeDesElevesFiltreParClasseEtAfficheLaClasse() throws Exception {
    UUID binta = creerEleve(A, "Binta");
    creerEleve(A, "Coumba");
    inscrit(awa, sixieme);
    inscrit(binta, ci);

    Reponse r = get(SECRETARIAT_A, A, ELEVES + "?classe=" + sixieme);
    assertThat(r.corps().path("elements"))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.path("prenoms").asText()).isEqualTo("Awa");
              assertThat(e.path("classe").asText()).isEqualTo("6E A");
            });
    Reponse tous = get(SECRETARIAT_A, A, ELEVES);
    assertThat(tous.corps().path("elements")).hasSize(3);
    assertThat(get(SURVEILLANT_A, A, "/api/v1/classes/" + ci + "/eleves").corps())
        .singleElement()
        .satisfies(e -> assertThat(e.path("prenoms").asText()).isEqualTo("Binta"));
    assertThat(get(COMPTABLE_A, A, "/api/v1/classes/" + ci + "/eleves").statut()).isEqualTo(403);
  }

  @Test
  void laScolariteSeLitParLeParentLieEtParLEleveLuiMeme() throws Exception {
    inscrit(awa, sixieme);
    UUID binta = creerEleve(A, "Binta");
    inscrit(binta, sixieme);
    UUID mere = creerResponsable(SECRETARIAT_A, A, "Mère", "770000001");
    lier(SECRETARIAT_A, A, awa, mere, "MERE", true);
    UUID compteMere = UUID.randomUUID();
    relierCompteParent(A, mere, compteMere);
    relierCompteEleve(binta, ELEVE_A);

    assertThat(get(compteMere, A, ELEVES + "/" + awa + "/inscriptions").corps()).hasSize(1);
    assertThat(get(compteMere, A, ELEVES + "/" + binta + "/inscriptions").statut()).isEqualTo(403);
    JsonNode enfants = get(compteMere, A, "/api/v1/moi/enfants").corps();
    assertThat(enfants.get(0).path("classe").asText()).isEqualTo("6E A");

    assertThat(get(ELEVE_A, A, ELEVES + "/" + binta + "/inscriptions").corps()).hasSize(1);
    assertThat(get(ELEVE_A, A, ELEVES + "/" + awa + "/inscriptions").statut()).isEqualTo(403);
    assertThat(get(COMPTABLE_A, A, ELEVES + "/" + awa + "/inscriptions").statut()).isEqualTo(403);
  }

  @Test
  void unEleveInscritNeSeSupprimePlus() throws Exception {
    inscrit(awa, sixieme);
    assertThat(delete(DIRECTEUR_A, A, ELEVES + "/" + awa).code()).isEqualTo("ELEVE_UTILISE");
  }

  @Test
  void lEcoleBNeVoitPasLesInscriptionsDeLEcoleANiSesClasses() throws Exception {
    UUID inscription = inscrit(awa, sixieme);
    assertThat(get(DIRECTEUR_B, B, INSCRIPTIONS + "/" + inscription).code())
        .isEqualTo("INSCRIPTION_INTROUVABLE");
    UUID eleveB = creerEleve(B, "Dior");
    Reponse r =
        post(
            DIRECTEUR_B,
            B,
            INSCRIPTIONS,
            corps(
                "eleveId", eleveB, "classeId", sixieme, "statut", "INSCRIT", "redoublant", false));
    assertThat(r.code()).isEqualTo("CLASSE_INTROUVABLE");
    assertThat(get(DIRECTEUR_B, B, "/api/v1/classes/" + sixieme + "/eleves").statut())
        .isEqualTo(404);
    // L'élève de B n'entre pas dans une classe de A, même par l'école A.
    assertThat(inscrire(SECRETARIAT_A, eleveB, sixieme, "INSCRIT", List.of()).code())
        .isEqualTo("ELEVE_INTROUVABLE");
  }

  @Test
  void leCompteEleveEstReserveAuCollegeEtAuLycee() throws Exception {
    UUID petit = creerEleve(A, "Binta");
    inscrit(petit, ci);
    assertThat(post(SECRETARIAT_A, A, ELEVES + "/" + petit + "/codes-activation", null).code())
        .isEqualTo("ACTIVATION_ELEVE_INTERDITE");
    UUID sansInscription = creerEleve(A, "Coumba");
    assertThat(
            post(SECRETARIAT_A, A, ELEVES + "/" + sansInscription + "/codes-activation", null)
                .code())
        .isEqualTo("ACTIVATION_ELEVE_INTERDITE");

    inscrit(awa, sixieme);
    Reponse code = post(SECRETARIAT_A, A, ELEVES + "/" + awa + "/codes-activation", null);
    assertThat(code.statut()).isEqualTo(201);
    UUID compte = UUID.randomUUID();
    Reponse activation =
        post(compte, A, "/api/v1/activations", corps("code", code.corps().path("code").asText()));
    assertThat(activation.statut()).as(activation.corps().toString()).isEqualTo(200);
    assertThat(activation.corps().path("role").asText()).isEqualTo("ELEVE");

    Reponse moi = get(compte, A, "/api/v1/moi/eleve");
    assertThat(moi.statut()).isEqualTo(200);
    assertThat(moi.corps().path("id").asText()).isEqualTo(awa.toString());
    assertThat(get(compte, A, ELEVES + "/" + awa + "/inscriptions").statut()).isEqualTo(200);
    assertThat(get(compte, A, ELEVES + "/" + petit).statut()).isEqualTo(403);
    assertThat(get(compte, A, ELEVES + "/" + awa + "/responsables").statut()).isEqualTo(403);
    assertThat(post(SECRETARIAT_A, A, ELEVES + "/" + awa + "/codes-activation", null).code())
        .isEqualTo("COMPTE_DEJA_RELIE");
  }
}
