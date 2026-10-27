package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.inscription.application.ActivationService;
import sn.ecole.inscription.application.ActivationService.CodeEmis;
import sn.ecole.inscription.application.ResponsableService;
import sn.ecole.inscription.application.ResponsableService.Saisie;
import sn.ecole.inscription.domain.Responsable;

@RestController
@RequestMapping("/api/v1/responsables")
class ResponsableController {

  private final ResponsableService service;
  private final ActivationService activation;
  private final ResponsableMapper mapper;

  ResponsableController(
      ResponsableService service, ActivationService activation, ResponsableMapper mapper) {
    this.service = service;
    this.activation = activation;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Recherche des responsables par téléphone (doublons) ou par nom (2 lettres au moins)")
  ResponseEntity<List<ResponsableDto>> rechercher(
      @RequestParam(name = "telephone", required = false) String telephone,
      @RequestParam(name = "nom", required = false) String nom) {
    return Reponses.ok(mapper.versDtos(service.rechercher(telephone, nom)));
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crée un responsable (propre à l'école)")
  ResponseEntity<ResponsableDto> creer(@Valid @RequestBody ResponsableRequete r) {
    Responsable resp = service.creer(saisie(r));
    return Reponses.cree(
        "/api/v1/responsables/" + resp.getId(), mapper.versDto(resp), resp.getVersion());
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Un responsable")
  ResponseEntity<ResponsableDto> lire(@PathVariable UUID id) {
    Responsable r = service.lire(id);
    return Reponses.ok(mapper.versDto(r), r.getVersion());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie un responsable (If-Match : version lue)")
  ResponseEntity<ResponsableDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ResponsableRequete r) {
    Responsable resp = service.modifier(id, Reponses.version(ifMatch), saisie(r));
    return Reponses.ok(mapper.versDto(resp), resp.getVersion());
  }

  @DeleteMapping(path = "/{id}/compte", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Détache le compte parent relié et lui retire le rôle PARENT (directeur ; perte d'accès)")
  ResponseEntity<ResponsableDto> delierCompte(@PathVariable UUID id) {
    Responsable r = service.delierCompte(id);
    return Reponses.ok(mapper.versDto(r), r.getVersion());
  }

  @PostMapping(path = "/{id}/codes-activation", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Émet un code d'activation à usage unique (7 jours) ; affiché une seule fois, les codes"
              + " précédents sont révoqués")
  ResponseEntity<CodeActivationDto> emettreCode(@PathVariable UUID id) {
    CodeEmis code = activation.emettrePourResponsable(id);
    return Reponses.cree(
        "/api/v1/responsables/" + id, new CodeActivationDto(code.code(), code.expireLe()));
  }

  private static Saisie saisie(ResponsableRequete r) {
    return new Saisie(r.prenoms(), r.nom(), r.telephone(), r.email(), r.profession());
  }
}
