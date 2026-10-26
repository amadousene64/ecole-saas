package sn.ecole.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

class GestionnaireErreursTest {

  record Demande(@NotBlank String nom) {}

  @RestController
  static class ControleurEssai {
    @GetMapping("/metier")
    void metier() {
      throw new ErreurMetier("BILLET_EXPIRE", TypeErreur.REGLE_METIER, "Le billet a expiré.");
    }

    @GetMapping("/introuvable")
    void introuvable() {
      throw new ErreurMetier("ELEVE_INTROUVABLE", TypeErreur.INTROUVABLE, "Élève introuvable.");
    }

    @GetMapping("/refus")
    void refus() {
      throw new AccessDeniedException("non");
    }

    @GetMapping("/version")
    void version() {
      throw new OptimisticLockingFailureException("version");
    }

    @GetMapping("/panne")
    void panne() {
      throw new IllegalStateException("détail interne à ne pas divulguer");
    }

    @PostMapping("/valider")
    void valider(@Valid @RequestBody Demande demande) {}
  }

  private final MockMvcTester mvc =
      MockMvcTester.of(
          java.util.List.of(new ControleurEssai()),
          builder -> builder.setControllerAdvice(new GestionnaireErreurs()).build());

  @Test
  void uneErreurMetierDevientUnProblemDetailAvecSonCode() {
    assertThat(mvc.get().uri("/metier"))
        .hasStatus(422)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .satisfies(
            json -> {
              assertThat(json).extractingPath("$.code").isEqualTo("BILLET_EXPIRE");
              assertThat(json).extractingPath("$.detail").isEqualTo("Le billet a expiré.");
              assertThat(json)
                  .extractingPath("$.type")
                  .isEqualTo("urn:ecole:probleme:BILLET_EXPIRE");
            });
    assertThat(mvc.get().uri("/introuvable")).hasStatus(404);
  }

  @Test
  void lesErreursTechniquesOntUnCodeStable() {
    assertThat(mvc.get().uri("/refus"))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("ACCES_REFUSE");
    assertThat(mvc.get().uri("/version"))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("CONFLIT_VERSION");
  }

  @Test
  void uneErreurInattendueNeDivulgueRien() {
    var resultat = mvc.get().uri("/panne");
    assertThat(resultat)
        .hasStatus(500)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("ERREUR_INTERNE");
    assertThat(resultat).bodyText().doesNotContain("détail interne");
  }

  @Test
  void laValidationListeLesChampsEnErreur() {
    assertThat(
            mvc.post()
                .uri("/valider")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"\"}"))
        .hasStatus(400)
        .bodyJson()
        .satisfies(
            json -> {
              assertThat(json).extractingPath("$.code").isEqualTo("VALIDATION");
              assertThat(json).extractingPath("$.erreurs[0].champ").isEqualTo("nom");
            });
  }

  @Test
  void lesErreursSpringStandardRecoiventUnCodeGenerique() {
    assertThat(mvc.get().uri("/valider"))
        .hasStatus(405)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("METHODE_NON_AUTORISEE");
    assertThat(mvc.post().uri("/valider").contentType(MediaType.APPLICATION_JSON).content("{"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("REQUETE_INVALIDE");
  }
}
