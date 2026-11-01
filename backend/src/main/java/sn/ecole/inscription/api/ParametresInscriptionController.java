package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.inscription.application.ParametresInscriptionService;
import sn.ecole.inscription.domain.ParametresInscription;

@RestController
@RequestMapping("/api/v1/parametres-inscription")
class ParametresInscriptionController {

  private final ParametresInscriptionService service;

  ParametresInscriptionController(ParametresInscriptionService service) {
    this.service = service;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Format des matricules et politique de capacité des classes")
  ResponseEntity<ParametresInscriptionDto> lire() {
    return reponse(service.lire());
  }

  @PutMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie les paramètres (directeur ; If-Match : version lue)")
  ResponseEntity<ParametresInscriptionDto> modifier(
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ParametresInscriptionRequete r) {
    return reponse(
        service.modifier(Reponses.version(ifMatch), r.formatMatricule(), r.politiqueCapacite()));
  }

  private static ResponseEntity<ParametresInscriptionDto> reponse(ParametresInscription p) {
    int annee = LocalDate.now(ZoneId.of("Africa/Dakar")).getYear();
    return Reponses.ok(
        new ParametresInscriptionDto(
            p.getFormatMatricule(), p.getPolitiqueCapacite(), p.format().generer(annee, 1)),
        ParametresInscriptionService.versionExposee(p));
  }
}
