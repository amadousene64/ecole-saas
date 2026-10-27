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
import sn.ecole.etablissement.application.CalendrierService;
import sn.ecole.etablissement.domain.EvenementCalendrier;

@RestController
@RequestMapping("/api/v1/annees-scolaires/{anneeId}/calendrier")
class CalendrierController {

  private final CalendrierService service;
  private final EvenementMapper mapper;

  CalendrierController(CalendrierService service, EvenementMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Jours fériés et vacances de l'année")
  List<EvenementDto> lister(@PathVariable UUID anneeId) {
    return mapper.versDtos(service.lister(anneeId));
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Ajoute un jour férié ou des vacances")
  ResponseEntity<EvenementDto> ajouter(
      @PathVariable UUID anneeId, @Valid @RequestBody EvenementRequete requete) {
    EvenementCalendrier evenement =
        service.ajouter(
            anneeId, requete.type(), requete.libelle(), requete.dateDebut(), requete.dateFin());
    return Reponses.cree(
        "/api/v1/annees-scolaires/" + anneeId + "/calendrier/" + evenement.getId(),
        mapper.versDto(evenement),
        evenement.getVersion());
  }

  @PutMapping(
      path = "/{evenementId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie un événement du calendrier (If-Match : version lue)")
  ResponseEntity<EvenementDto> modifier(
      @PathVariable UUID anneeId,
      @PathVariable UUID evenementId,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EvenementRequete requete) {
    EvenementCalendrier evenement =
        service.modifier(
            anneeId,
            evenementId,
            Reponses.version(ifMatch),
            requete.type(),
            requete.libelle(),
            requete.dateDebut(),
            requete.dateFin());
    return Reponses.ok(mapper.versDto(evenement), evenement.getVersion());
  }

  @DeleteMapping("/{evenementId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) un événement du calendrier")
  void supprimer(@PathVariable UUID anneeId, @PathVariable UUID evenementId) {
    service.supprimer(anneeId, evenementId);
  }
}
