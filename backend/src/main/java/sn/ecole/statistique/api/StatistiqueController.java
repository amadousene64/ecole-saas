package sn.ecole.statistique.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.statistique.application.TableauDeBordService;
import sn.ecole.statistique.domain.PeriodeCourbe;

@RestController
@RequestMapping("/api/v1/statistiques")
class StatistiqueController {

  private final TableauDeBordService service;
  private final TableauDeBordMapper mapper;

  StatistiqueController(TableauDeBordService service, TableauDeBordMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(path = "/direction", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Tableau de bord de la direction (année en cours)",
      description =
          "Effectifs par cycle et par niveau, inscriptions par statut, dossiers complets, familles"
              + " connectées et courbe des inscriptions par semaine. Directeur seulement. Valeurs"
              + " calculées au plus 5 minutes auparavant (champ calculeLe). Filtres : période de la"
              + " courbe et cycle (tous par défaut).")
  ResponseEntity<TableauDeBordDto> direction(
      @RequestParam(defaultValue = "ANNEE") PeriodeCourbe periode,
      @RequestParam(required = false) CodeCycle cycle) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(mapper.versDto(service.direction(periode, cycle)));
  }
}
