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
import sn.ecole.etablissement.application.SalleService;
import sn.ecole.etablissement.domain.Salle;

@RestController
@RequestMapping("/api/v1/salles")
class SalleController {

  private final SalleService service;
  private final SalleMapper mapper;

  SalleController(SalleService service, SalleMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Salles de l'école")
  List<SalleDto> lister() {
    return mapper.versDtos(service.lister());
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Une salle")
  ResponseEntity<SalleDto> lire(@PathVariable UUID id) {
    Salle salle = service.lire(id);
    return Reponses.ok(mapper.versDto(salle), salle.getVersion());
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crée une salle")
  ResponseEntity<SalleDto> creer(@Valid @RequestBody SalleRequete requete) {
    Salle salle = service.creer(requete.nom(), requete.capacite());
    return Reponses.cree(
        "/api/v1/salles/" + salle.getId(), mapper.versDto(salle), salle.getVersion());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie une salle (If-Match : version lue)")
  ResponseEntity<SalleDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody SalleRequete requete) {
    Salle salle =
        service.modifier(
            id,
            Reponses.version(ifMatch),
            requete.nom(),
            requete.capacite(),
            requete.actif() == null || requete.actif());
    return Reponses.ok(mapper.versDto(salle), salle.getVersion());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) une salle non utilisée")
  void supprimer(@PathVariable UUID id) {
    service.supprimer(id);
  }
}
