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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.etablissement.application.ClasseService;
import sn.ecole.etablissement.application.ClasseService.Saisie;
import sn.ecole.etablissement.domain.Classe;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

@RestController
@RequestMapping("/api/v1/classes")
class ClasseController {

  private final ClasseService service;
  private final ClasseMapper mapper;
  private final OptionEnseignementMapper optionMapper;

  ClasseController(
      ClasseService service, ClasseMapper mapper, OptionEnseignementMapper optionMapper) {
    this.service = service;
    this.mapper = mapper;
    this.optionMapper = optionMapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Classes d'une année (par défaut l'année en cours), filtrables par niveau")
  List<ClasseDto> lister(
      @RequestParam(name = "annee", required = false) UUID anneeId,
      @RequestParam(name = "niveau", required = false) UUID niveauId) {
    return mapper.versDtos(service.lister(anneeId, niveauId));
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Une classe")
  ResponseEntity<ClasseDto> lire(@PathVariable UUID id) {
    Classe classe = service.lire(id);
    return Reponses.ok(mapper.versDto(classe), classe.getVersion());
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crée une classe dans une année non clôturée")
  ResponseEntity<ClasseDto> creer(@Valid @RequestBody ClasseRequete requete) {
    if (requete.anneeId() == null) {
      throw new ErreurMetier(
          "CHAMP_INVALIDE", TypeErreur.INVALIDE, "L'année de la classe est obligatoire.");
    }
    Classe classe = service.creer(requete.anneeId(), saisie(requete));
    return Reponses.cree(
        "/api/v1/classes/" + classe.getId(), mapper.versDto(classe), classe.getVersion());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie une classe (If-Match : version lue)")
  ResponseEntity<ClasseDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ClasseRequete requete) {
    Classe classe = service.modifier(id, Reponses.version(ifMatch), saisie(requete));
    return Reponses.ok(mapper.versDto(classe), classe.getVersion());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) une classe")
  void supprimer(@PathVariable UUID id) {
    service.supprimer(id);
  }

  @GetMapping(path = "/{id}/options", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Options d'enseignement proposées par la classe")
  List<OptionEnseignementDto> options(@PathVariable UUID id) {
    return optionMapper.versDtos(service.options(id));
  }

  @PutMapping(
      path = "/{id}/options",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Remplace les options proposées par la classe")
  List<OptionEnseignementDto> definirOptions(
      @PathVariable UUID id, @Valid @RequestBody ClasseOptionsRequete requete) {
    return optionMapper.versDtos(service.definirOptions(id, requete.optionIds()));
  }

  private static Saisie saisie(ClasseRequete requete) {
    return new Saisie(
        requete.niveauId(),
        requete.serieId(),
        requete.nom(),
        requete.capacite(),
        requete.salleId());
  }
}
