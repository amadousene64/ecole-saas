package sn.ecole.personnel.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.personnel.application.AffectationService;
import sn.ecole.personnel.application.AnnuairePersonnel;

/** Affectations du personnel, annuaire des affectables, « Mes classes ». */
@RestController
class AffectationController {

  private final AffectationService service;
  private final AnnuairePersonnel annuaire;
  private final AffectationMapper mapper;

  AffectationController(
      AffectationService service, AnnuairePersonnel annuaire, AffectationMapper mapper) {
    this.service = service;
    this.annuaire = annuaire;
    this.mapper = mapper;
  }

  @GetMapping(path = "/api/v1/affectations", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Affectations de l'année, en cours et terminées (historique)")
  ResponseEntity<List<AffectationDto>> lister(@RequestParam("annee") UUID anneeId) {
    return Reponses.ok(mapper.versDtos(service.lister(anneeId)));
  }

  @PostMapping(
      path = "/api/v1/affectations",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary =
          "Affecte un enseignant (matière dans une classe, ou titulaire) ou un surveillant (un ou"
              + " plusieurs cycles) ; une affectation par cycle")
  List<AffectationDto> creer(@Valid @RequestBody AffectationRequete r) {
    List<UUID> ids =
        service
            .creer(
                new AffectationService.Saisie(
                    r.type(),
                    r.anneeId(),
                    r.utilisateurId(),
                    r.classeId(),
                    r.matiereId(),
                    r.cycles(),
                    r.debut(),
                    r.fin()))
            .stream()
            .map(a -> a.getId())
            .toList();
    return mapper.versDtos(
        service.lister(r.anneeId()).stream()
            .filter(v -> ids.contains(v.affectation().getId()))
            .toList());
  }

  @PutMapping(
      path = "/api/v1/affectations/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Corrige la période d'une affectation (If-Match : version lue)")
  ResponseEntity<Void> changerPeriode(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody PeriodeRequete r) {
    service.changerPeriode(id, Reponses.version(ifMatch), r.debut(), r.fin());
    return ResponseEntity.noContent().build();
  }

  @PostMapping(path = "/api/v1/affectations/{id}/fin", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Termine une affectation (aujourd'hui par défaut : effet immédiat) ; annulée si elle"
              + " n'avait pas commencé (If-Match : version lue)")
  ResponseEntity<Void> terminer(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @RequestBody(required = false) FinRequete r) {
    service.terminer(id, Reponses.version(ifMatch), r == null ? null : r.fin());
    return ResponseEntity.noContent().build();
  }

  @GetMapping(path = "/api/v1/personnel/affectables", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Enseignants et surveillants de l'école, à affecter")
  ResponseEntity<List<MembreDto>> affectables() {
    return Reponses.ok(mapper.versMembres(annuaire.affectables()));
  }

  @GetMapping(path = "/api/v1/moi/affectations", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Mes affectations en cours (enseignant, surveillant)")
  ResponseEntity<List<AffectationDto>> miennes() {
    return Reponses.ok(mapper.versDtos(service.miennes()));
  }
}
