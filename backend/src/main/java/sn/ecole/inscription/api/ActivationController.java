package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.inscription.application.ActivationService;
import sn.ecole.inscription.application.LienParentalService;

/**
 * Espace parent et élève : activation du compte par le code remis par l'école, liste des enfants.
 *
 * <p>{@code POST /api/v1/activations} passe par une chaîne de sécurité dédiée (jeton obligatoire,
 * école active, aucune appartenance exigée) : l'utilisateur n'est pas encore membre de l'école.
 */
@RestController
class ActivationController {

  private final ActivationService activation;
  private final LienParentalService liens;
  private final EleveMapper eleveMapper;

  ActivationController(
      ActivationService activation, LienParentalService liens, EleveMapper eleveMapper) {
    this.activation = activation;
    this.liens = liens;
    this.eleveMapper = eleveMapper;
  }

  @PostMapping(
      path = "/api/v1/activations",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Relie le compte connecté au responsable (ou à l'élève) désigné par le code remis par"
              + " l'école ; essais limités")
  ResponseEntity<ActivationDto> activer(
      @Valid @RequestBody ActivationRequete r, HttpServletRequest requete) {
    // Adresse du client : derrière un proxy de confiance (ECOLE_PROXYS_INTERNES), celle de
    // X-Forwarded-For (server.forward-headers-strategy=native).
    var resultat = activation.activer(r.code(), requete.getRemoteAddr());
    return Reponses.ok(new ActivationDto(resultat.role()));
  }

  @GetMapping(path = "/api/v1/moi/enfants", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Enfants du parent connecté dans l'école courante")
  ResponseEntity<List<EleveResumeDto>> mesEnfants() {
    return Reponses.ok(eleveMapper.versResumes(liens.enfantsDuParentConnecte()));
  }
}
