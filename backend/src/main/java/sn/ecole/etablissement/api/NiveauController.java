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
import sn.ecole.etablissement.application.NiveauService;
import sn.ecole.etablissement.application.NiveauService.VueNiveau;

@RestController
@RequestMapping("/api/v1/niveaux")
class NiveauController {

  private final NiveauService service;
  private final NiveauMapper mapper;

  NiveauController(NiveauService service, NiveauMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Niveaux d'études, de CI à la Terminale, avec leurs séries")
  List<NiveauDto> lister() {
    return mapper.versDtos(service.lister());
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Un niveau")
  ResponseEntity<NiveauDto> lire(@PathVariable UUID id) {
    return reponse(service.lire(id));
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crée un niveau")
  ResponseEntity<NiveauDto> creer(@Valid @RequestBody CreerNiveauRequete requete) {
    VueNiveau vue =
        service.creer(
            requete.cycleId(),
            requete.code(),
            requete.libelle(),
            requete.ordre(),
            requete.baseMoyenne());
    return Reponses.cree(
        "/api/v1/niveaux/" + vue.niveau().getId(), mapper.versDto(vue), vue.niveau().getVersion());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie un niveau (If-Match : version lue)")
  ResponseEntity<NiveauDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ModifierNiveauRequete requete) {
    return reponse(
        service.modifier(
            id,
            Reponses.version(ifMatch),
            requete.libelle(),
            requete.ordre(),
            requete.baseMoyenne(),
            requete.actif()));
  }

  @PutMapping(
      path = "/{id}/series",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Définit les séries proposées par un niveau")
  ResponseEntity<NiveauDto> definirSeries(
      @PathVariable UUID id, @Valid @RequestBody DefinirSeriesRequete requete) {
    return reponse(service.definirSeries(id, requete.serieIds()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) un niveau sans classe")
  void supprimer(@PathVariable UUID id) {
    service.supprimer(id);
  }

  private ResponseEntity<NiveauDto> reponse(VueNiveau vue) {
    return Reponses.ok(mapper.versDto(vue), vue.niveau().getVersion());
  }
}
