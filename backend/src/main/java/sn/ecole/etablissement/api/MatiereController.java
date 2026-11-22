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
import sn.ecole.etablissement.application.MatiereService;
import sn.ecole.etablissement.domain.Matiere;

@RestController
@RequestMapping("/api/v1/matieres")
class MatiereController {

  private final MatiereService service;
  private final MatiereMapper mapper;

  MatiereController(MatiereService service, MatiereMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Matières de l'école, dans l'ordre d'affichage")
  List<MatiereDto> lister() {
    return mapper.versDtos(service.lister());
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Crée une matière")
  ResponseEntity<MatiereDto> creer(@Valid @RequestBody MatiereRequete requete) {
    Matiere matiere = service.creer(saisie(requete));
    return Reponses.cree(
        "/api/v1/matieres/" + matiere.getId(), mapper.versDto(matiere), matiere.getVersion());
  }

  @PostMapping(path = "/modele", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Ajoute les matières usuelles du modèle sénégalais encore absentes (par code) ; renvoie"
              + " celles ajoutées")
  List<MatiereDto> ajouterModele() {
    return mapper.versDtos(service.ajouterModele());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie une matière (If-Match : version lue)")
  ResponseEntity<MatiereDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody MatiereRequete requete) {
    Matiere matiere =
        service.modifier(
            id,
            Reponses.version(ifMatch),
            saisie(requete),
            requete.actif() == null || requete.actif());
    return Reponses.ok(mapper.versDto(matiere), matiere.getVersion());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) une matière")
  void supprimer(@PathVariable UUID id) {
    service.supprimer(id);
  }

  private static MatiereService.Saisie saisie(MatiereRequete r) {
    return new MatiereService.Saisie(r.code(), r.libelle(), r.groupe(), r.cycles(), r.ordre());
  }
}
