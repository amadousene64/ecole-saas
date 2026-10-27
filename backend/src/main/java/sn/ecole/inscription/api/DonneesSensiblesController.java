package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.inscription.application.DossierSensibleService;
import sn.ecole.inscription.application.DossierSensibleService.Contenu;
import sn.ecole.inscription.application.DossierSensibleService.Etat;
import sn.ecole.inscription.application.DossierSensibleService.Modification;

/** Santé, allergies, situation familiale : chiffrées en base, lectures journalisées. */
@RestController
@RequestMapping("/api/v1/eleves/{eleveId}/donnees-sensibles")
class DonneesSensiblesController {

  private final DossierSensibleService service;

  DonneesSensiblesController(DossierSensibleService service) {
    this.service = service;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Lit les données sensibles (directeur, censeur) ; chaque lecture est journalisée")
  ResponseEntity<DonneesSensiblesDto> lire(@PathVariable UUID eleveId) {
    Contenu c = service.lire(eleveId);
    return Reponses.ok(
        new DonneesSensiblesDto(c.sante(), c.allergies(), c.situationFamiliale()), c.version());
  }

  @GetMapping(path = "/etat", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Champs renseignés, sans leur contenu (pour le secrétariat)")
  ResponseEntity<EtatDonneesSensiblesDto> etat(@PathVariable UUID eleveId) {
    return reponse(service.etat(eleveId));
  }

  @PutMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Complète ou corrige les données sensibles (If-Match) : champ absent inchangé, vide"
              + " effacé ; renvoie seulement l'état")
  ResponseEntity<EtatDonneesSensiblesDto> modifier(
      @PathVariable UUID eleveId,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody DonneesSensiblesRequete r) {
    return reponse(
        service.modifier(
            eleveId,
            Reponses.version(ifMatch),
            new Modification(r.sante(), r.allergies(), r.situationFamiliale())));
  }

  private static ResponseEntity<EtatDonneesSensiblesDto> reponse(Etat e) {
    return Reponses.ok(
        new EtatDonneesSensiblesDto(e.sante(), e.allergies(), e.situationFamiliale()), e.version());
  }
}
