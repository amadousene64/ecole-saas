package sn.ecole.etablissement.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.etablissement.application.SerieService;
import sn.ecole.etablissement.domain.Serie;

@RestController
@RequestMapping("/api/v1/series")
class SerieController {

  private final SerieService service;
  private final SerieMapper mapper;

  SerieController(SerieService service, SerieMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Séries du lycée")
  List<SerieDto> lister() {
    return mapper.versDtos(service.lister());
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crée une série")
  ResponseEntity<SerieDto> creer(@Valid @RequestBody SerieRequete requete) {
    Serie serie = service.creer(requete.code(), requete.libelle());
    return Reponses.cree(
        "/api/v1/series/" + serie.getId(), mapper.versDto(serie), serie.getVersion());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie une série (If-Match : version lue)")
  ResponseEntity<SerieDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody SerieRequete requete) {
    Serie serie =
        service.modifier(
            id,
            Reponses.version(ifMatch),
            requete.code(),
            requete.libelle(),
            requete.actif() == null || requete.actif());
    return Reponses.ok(mapper.versDto(serie), serie.getVersion());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) une série non utilisée")
  void supprimer(@PathVariable UUID id) {
    service.supprimer(id);
  }
}
