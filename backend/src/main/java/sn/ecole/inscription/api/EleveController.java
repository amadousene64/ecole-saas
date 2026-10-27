package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.ecole.inscription.application.EleveService;
import sn.ecole.inscription.application.EleveService.Saisie;
import sn.ecole.inscription.application.EleveService.VueEleve;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.shared.domain.PageCurseur;

@RestController
@RequestMapping("/api/v1/eleves")
class EleveController {

  private final EleveService service;
  private final EleveMapper mapper;

  EleveController(EleveService service, EleveMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Recherche des élèves (nom, prénoms ou matricule), paginée par curseur")
  ResponseEntity<PageDto<EleveResumeDto>> rechercher(
      @RequestParam(name = "recherche", required = false) String recherche,
      @RequestParam(name = "curseur", required = false) String curseur,
      @RequestParam(name = "taille", required = false) Integer taille) {
    PageCurseur<Eleve> page = service.rechercher(recherche, null, curseur, taille);
    return Reponses.ok(new PageDto<>(mapper.versResumes(page.elements()), page.curseurSuivant()));
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crée un élève ; le matricule est attribué selon le format de l'école")
  ResponseEntity<EleveDto> creer(@Valid @RequestBody EleveRequete requete) {
    Eleve eleve = service.creer(saisie(requete));
    return Reponses.cree(
        "/api/v1/eleves/" + eleve.getId(),
        mapper.versDto(new VueEleve(eleve, null)),
        eleve.getVersion());
  }

  @GetMapping(path = "/{eleveId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Identité d'un élève, avec l'URL signée de sa photo")
  ResponseEntity<EleveDto> lire(@PathVariable UUID eleveId) {
    return reponse(service.lire(eleveId));
  }

  @PutMapping(
      path = "/{eleveId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Corrige l'identité (If-Match : version lue) ; le matricule ne change pas")
  ResponseEntity<EleveDto> modifier(
      @PathVariable UUID eleveId,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EleveRequete requete) {
    return reponse(service.modifier(eleveId, Reponses.version(ifMatch), saisie(requete)));
  }

  @DeleteMapping("/{eleveId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) un élève créé par erreur, sans donnée rattachée")
  void supprimer(@PathVariable UUID eleveId) {
    service.supprimer(eleveId);
  }

  @PutMapping(
      path = "/{eleveId}/photo",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Dépose la photo (PNG ou JPEG, 1 Mo et 2000 px au plus)")
  ResponseEntity<EleveDto> deposerPhoto(
      @PathVariable UUID eleveId, @RequestPart("fichier") MultipartFile fichier)
      throws IOException {
    return reponse(service.deposerPhoto(eleveId, fichier.getBytes()));
  }

  @DeleteMapping(path = "/{eleveId}/photo", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Retire la photo")
  ResponseEntity<EleveDto> supprimerPhoto(@PathVariable UUID eleveId) {
    return reponse(service.supprimerPhoto(eleveId));
  }

  private ResponseEntity<EleveDto> reponse(VueEleve vue) {
    return Reponses.ok(mapper.versDto(vue), vue.eleve().getVersion());
  }

  static Saisie saisie(EleveRequete r) {
    return new Saisie(
        r.prenoms(),
        r.nom(),
        r.sexe(),
        r.dateNaissance(),
        r.lieuNaissance(),
        r.nationalite(),
        r.identifiantNational());
  }
}
