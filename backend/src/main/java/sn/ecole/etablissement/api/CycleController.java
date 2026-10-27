package sn.ecole.etablissement.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
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
import sn.ecole.etablissement.application.CycleService;
import sn.ecole.etablissement.application.CycleService.Modification;
import sn.ecole.etablissement.domain.Cycle;

@RestController
@RequestMapping("/api/v1/cycles")
class CycleController {

  private final CycleService service;
  private final CycleMapper mapper;

  CycleController(CycleService service, CycleMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Cycles et leurs paramètres de notation")
  List<CycleDto> lister() {
    return mapper.versDtos(service.lister());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie les paramètres d'un cycle (directeur ; If-Match : version lue)")
  ResponseEntity<CycleDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ModifierCycleRequete requete) {
    Cycle cycle =
        service.modifier(
            id,
            Reponses.version(ifMatch),
            new Modification(
                requete.libelle(),
                requete.typePeriode(),
                requete.modeNotation(),
                requete.baseMoyenne()));
    return Reponses.ok(mapper.versDto(cycle), cycle.getVersion());
  }
}
