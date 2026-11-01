package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.ecole.inscription.application.ImportElevesService;
import sn.ecole.inscription.application.ImportElevesService.Rapport;
import sn.ecole.inscription.application.ImportElevesService.Resultat;

/** Import d'une liste d'élèves (CSV ou XLSX) : validation à blanc, puis import tout ou rien. */
@RestController
@RequestMapping("/api/v1/imports-eleves")
class ImportElevesController {

  private final ImportElevesService service;

  ImportElevesController(ImportElevesService service) {
    this.service = service;
  }

  @PostMapping(
      path = "/validation",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Valide à blanc un fichier CSV ou XLSX (2 Mo, 2000 lignes) : rapport ligne par ligne,"
              + " rien n'est écrit")
  ResponseEntity<RapportImportDto> valider(
      @RequestPart("fichier") MultipartFile fichier,
      @RequestParam(name = "annee", required = false) UUID anneeId)
      throws IOException {
    return Reponses.ok(versDto(service.analyser(fichier.getBytes(), anneeId)));
  }

  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Importe le fichier validé (même empreinte), tout ou rien ; 422 avec le rapport s'il"
              + " reste des erreurs")
  @ApiResponse(
      responseCode = "201",
      description = "Import effectué",
      content = @Content(schema = @Schema(implementation = ResultatImportDto.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Le fichier contient des erreurs : rien n'est écrit, voici le rapport",
      content = @Content(schema = @Schema(implementation = RapportImportDto.class)))
  ResponseEntity<?> importer(
      @RequestPart("fichier") MultipartFile fichier,
      @RequestParam("empreinte") String empreinte,
      @RequestParam(name = "annee", required = false) UUID anneeId)
      throws IOException {
    Resultat r = service.importer(fichier.getBytes(), empreinte, anneeId);
    RapportImportDto rapport = versDto(r.rapport());
    if (!r.applique()) {
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
          .cacheControl(CacheControl.noStore())
          .body(rapport);
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(new ResultatImportDto(r.elevesCrees(), r.inscriptions(), r.responsables(), rapport));
  }

  private static RapportImportDto versDto(Rapport r) {
    return new RapportImportDto(
        r.empreinte(),
        r.lignes(),
        r.erreurs(),
        r.avertissements(),
        r.valide(),
        r.colonnesIgnorees(),
        r.details().stream()
            .map(
                l ->
                    new RapportImportDto.LigneImportDto(
                        l.numero(), l.statut().name(), l.messages()))
            .toList());
  }
}
