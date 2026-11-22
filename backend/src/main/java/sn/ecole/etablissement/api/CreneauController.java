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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.etablissement.application.CreneauService;
import sn.ecole.etablissement.domain.Creneau;

/** Grille horaire de chaque cycle. */
@RestController
class CreneauController {

  private final CreneauService service;
  private final CreneauMapper mapper;

  CreneauController(CreneauService service, CreneauMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(
      path = "/api/v1/cycles/{cycleId}/creneaux",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Grille horaire du cycle, par heure de début")
  List<CreneauDto> lister(@PathVariable UUID cycleId) {
    return mapper.versDtos(service.lister(cycleId));
  }

  @PostMapping(
      path = "/api/v1/cycles/{cycleId}/creneaux",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Ajoute un créneau à la grille du cycle (sans chevauchement)")
  ResponseEntity<CreneauDto> creer(
      @PathVariable UUID cycleId, @Valid @RequestBody CreneauRequete requete) {
    Creneau creneau = service.creer(cycleId, saisie(requete));
    return Reponses.cree(
        "/api/v1/creneaux/" + creneau.getId(), mapper.versDto(creneau), creneau.getVersion());
  }

  @PostMapping(
      path = "/api/v1/cycles/{cycleId}/creneaux/copie",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Recopie la grille d'un autre cycle dans ce cycle, encore vide")
  List<CreneauDto> copier(
      @PathVariable UUID cycleId, @Valid @RequestBody CopieGrilleRequete requete) {
    return mapper.versDtos(service.copier(requete.depuisCycleId(), cycleId));
  }

  @PutMapping(
      path = "/api/v1/creneaux/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie un créneau (If-Match : version lue)")
  ResponseEntity<CreneauDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody CreneauRequete requete) {
    Creneau creneau = service.modifier(id, Reponses.version(ifMatch), saisie(requete));
    return Reponses.ok(mapper.versDto(creneau), creneau.getVersion());
  }

  @DeleteMapping("/api/v1/creneaux/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) un créneau")
  void supprimer(@PathVariable UUID id) {
    service.supprimer(id);
  }

  private static CreneauService.Saisie saisie(CreneauRequete r) {
    return new CreneauService.Saisie(r.debut(), r.fin(), r.jours(), r.type(), r.libelle());
  }
}
