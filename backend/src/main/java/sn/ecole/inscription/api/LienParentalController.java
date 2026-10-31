package sn.ecole.inscription.api;

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
import sn.ecole.inscription.application.LienParentalService;
import sn.ecole.inscription.application.LienParentalService.VueLien;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/** Responsables d'un élève et fratrie. */
@RestController
@RequestMapping("/api/v1/eleves/{eleveId}")
class LienParentalController {

  private final LienParentalService service;
  private final ResponsableMapper mapper;
  private final EleveMapper eleveMapper;

  LienParentalController(
      LienParentalService service, ResponsableMapper mapper, EleveMapper eleveMapper) {
    this.service = service;
    this.mapper = mapper;
    this.eleveMapper = eleveMapper;
  }

  @GetMapping(path = "/responsables", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Responsables de l'élève (comptable : payeurs seulement ; parent : son propre lien)")
  ResponseEntity<List<LienParentalDto>> lister(@PathVariable UUID eleveId) {
    return Reponses.ok(mapper.versDtosLiens(service.lister(eleveId)));
  }

  @PostMapping(
      path = "/responsables",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Lie un responsable à l'élève, avec ses droits")
  ResponseEntity<LienParentalDto> ajouter(
      @PathVariable UUID eleveId, @Valid @RequestBody LienParentalRequete r) {
    if (r.responsableId() == null) {
      throw new ErreurMetier(
          "CHAMP_INVALIDE", TypeErreur.INVALIDE, "Le responsable est obligatoire.");
    }
    VueLien vue = service.ajouter(eleveId, r.responsableId(), r.type(), droits(r));
    return Reponses.cree(
        "/api/v1/eleves/" + eleveId + "/responsables/" + vue.lien().getId(),
        mapper.versDto(vue),
        vue.lien().getVersion());
  }

  @PutMapping(
      path = "/responsables/{lienId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie le type et les droits du lien (If-Match : version lue)")
  ResponseEntity<LienParentalDto> modifier(
      @PathVariable UUID eleveId,
      @PathVariable UUID lienId,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody LienParentalRequete r) {
    return reponse(
        service.modifier(eleveId, lienId, Reponses.version(ifMatch), r.type(), droits(r)));
  }

  @PutMapping(
      path = "/responsables/{lienId}/acces-espace-parent",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Accorde ou retire l'accès à l'espace parent (directeur ; sans accès, ni consultation"
              + " ni notification)")
  ResponseEntity<LienParentalDto> definirAcces(
      @PathVariable UUID eleveId,
      @PathVariable UUID lienId,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody AccesEspaceParentRequete r) {
    return reponse(
        service.definirAccesEspaceParent(eleveId, lienId, Reponses.version(ifMatch), r.acces()));
  }

  @DeleteMapping("/responsables/{lienId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Retire le lien (pas le dernier responsable légal)")
  void supprimer(@PathVariable UUID eleveId, @PathVariable UUID lienId) {
    service.supprimer(eleveId, lienId);
  }

  @GetMapping(path = "/fratrie", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Frères et sœurs : élèves ayant un responsable en commun")
  ResponseEntity<List<EleveResumeDto>> fratrie(@PathVariable UUID eleveId) {
    return Reponses.ok(eleveMapper.versResumes(service.fratrie(eleveId)));
  }

  private ResponseEntity<LienParentalDto> reponse(VueLien vue) {
    return Reponses.ok(mapper.versDto(vue), vue.lien().getVersion());
  }

  private static LienParental.Droits droits(LienParentalRequete r) {
    return new LienParental.Droits(
        r.responsableLegal(),
        r.payeur(),
        r.contactUrgence(),
        r.autoriseRecuperer(),
        r.recoitNotifications());
  }
}
