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
import sn.ecole.etablissement.application.AnneeScolaireService;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

@RestController
@RequestMapping("/api/v1/annees-scolaires")
class AnneeScolaireController {

  private final AnneeScolaireService service;
  private final AnneeScolaireMapper mapper;

  AnneeScolaireController(AnneeScolaireService service, AnneeScolaireMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Années scolaires, la plus récente d'abord")
  List<AnneeScolaireDto> lister() {
    return mapper.versDtos(service.lister());
  }

  @GetMapping(path = "/en-cours", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Année scolaire en cours (404 s'il n'y en a pas)")
  ResponseEntity<AnneeScolaireDto> enCours() {
    AnneeScolaire annee =
        service
            .enCours()
            .orElseThrow(
                () ->
                    new ErreurMetier(
                        "AUCUNE_ANNEE_EN_COURS",
                        TypeErreur.INTROUVABLE,
                        "Aucune année scolaire n'est en cours."));
    return reponse(annee);
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Une année scolaire")
  ResponseEntity<AnneeScolaireDto> lire(@PathVariable UUID id) {
    return reponse(service.lire(id));
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Crée une année en préparation, avec ses périodes par cycle et ses jours fériés")
  ResponseEntity<AnneeScolaireDto> creer(@Valid @RequestBody AnneeScolaireRequete requete) {
    AnneeScolaire annee = service.creer(requete.libelle(), requete.dateDebut(), requete.dateFin());
    return Reponses.cree(
        "/api/v1/annees-scolaires/" + annee.getId(), mapper.versDto(annee), annee.getVersion());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie le libellé et les dates (If-Match : version lue)")
  ResponseEntity<AnneeScolaireDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody AnneeScolaireRequete requete) {
    return reponse(
        service.modifier(
            id,
            Reponses.version(ifMatch),
            requete.libelle(),
            requete.dateDebut(),
            requete.dateFin()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) une année en préparation et sans classe")
  void supprimer(@PathVariable UUID id) {
    service.supprimer(id);
  }

  @PostMapping(path = "/{id}/ouverture", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Ouvre l'année (directeur) : périodes complètes, aucune autre année en cours")
  ResponseEntity<AnneeScolaireDto> ouvrir(@PathVariable UUID id) {
    return reponse(service.ouvrir(id));
  }

  @PostMapping(path = "/{id}/cloture", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Clôture l'année (directeur) : définitif, lecture seule ensuite")
  ResponseEntity<AnneeScolaireDto> cloturer(@PathVariable UUID id) {
    return reponse(service.cloturer(id));
  }

  private ResponseEntity<AnneeScolaireDto> reponse(AnneeScolaire annee) {
    return Reponses.ok(mapper.versDto(annee), annee.getVersion());
  }
}
