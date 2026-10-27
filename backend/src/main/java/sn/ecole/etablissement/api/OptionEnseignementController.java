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
import sn.ecole.etablissement.application.OptionEnseignementService;
import sn.ecole.etablissement.application.OptionEnseignementService.Saisie;
import sn.ecole.etablissement.domain.OptionEnseignement;

@RestController
@RequestMapping("/api/v1/options-enseignement")
class OptionEnseignementController {

  private final OptionEnseignementService service;
  private final OptionEnseignementMapper mapper;

  OptionEnseignementController(OptionEnseignementService service, OptionEnseignementMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Catalogue des options d'enseignement (LV2, arts…)")
  List<OptionEnseignementDto> lister() {
    return mapper.versDtos(service.lister());
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Ajoute une option au catalogue")
  ResponseEntity<OptionEnseignementDto> creer(
      @Valid @RequestBody OptionEnseignementRequete requete) {
    OptionEnseignement option = service.creer(saisie(requete));
    return Reponses.cree(
        "/api/v1/options-enseignement/" + option.getId(),
        mapper.versDto(option),
        option.getVersion());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie une option (If-Match : version lue)")
  ResponseEntity<OptionEnseignementDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody OptionEnseignementRequete requete) {
    OptionEnseignement option = service.modifier(id, Reponses.version(ifMatch), saisie(requete));
    return Reponses.ok(mapper.versDto(option), option.getVersion());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) une option proposée par aucune classe")
  void supprimer(@PathVariable UUID id) {
    service.supprimer(id);
  }

  private static Saisie saisie(OptionEnseignementRequete r) {
    return new Saisie(r.code(), r.libelle(), r.groupe(), r.actif() == null || r.actif());
  }
}
