package sn.ecole.etablissement.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.etablissement.application.PeriodeService;
import sn.ecole.etablissement.domain.Periode;

@RestController
@RequestMapping("/api/v1/annees-scolaires/{anneeId}/periodes")
class PeriodeController {

  private final PeriodeService service;
  private final PeriodeMapper mapper;

  PeriodeController(PeriodeService service, PeriodeMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Périodes de l'année, par cycle puis par numéro")
  List<PeriodeDto> lister(@PathVariable UUID anneeId) {
    return mapper.versDtos(service.lister(anneeId));
  }

  @PostMapping(path = "/generation", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Régénère les périodes selon le type de période de chaque cycle (préparation)")
  List<PeriodeDto> generer(@PathVariable UUID anneeId) {
    return mapper.versDtos(service.generer(anneeId));
  }

  @PutMapping(
      path = "/{periodeId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Ajuste une période : dans l'année, sans chevauchement (If-Match)")
  ResponseEntity<PeriodeDto> modifier(
      @PathVariable UUID anneeId,
      @PathVariable UUID periodeId,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ModifierPeriodeRequete requete) {
    Periode periode =
        service.modifier(
            anneeId,
            periodeId,
            Reponses.version(ifMatch),
            requete.libelle(),
            requete.dateDebut(),
            requete.dateFin());
    return Reponses.ok(mapper.versDto(periode), periode.getVersion());
  }
}
