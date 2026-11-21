package sn.ecole.etablissement.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.ecole.etablissement.application.EtablissementService;
import sn.ecole.etablissement.application.EtablissementService.Modification;
import sn.ecole.etablissement.application.EtablissementService.VueEtablissement;
import sn.ecole.etablissement.domain.TypeImage;

@RestController
@RequestMapping("/api/v1/etablissement")
class EtablissementController {

  private final EtablissementService service;
  private final EtablissementMapper mapper;

  EtablissementController(EtablissementService service, EtablissementMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Profil de l'école courante, avec les URL signées de ses images")
  ResponseEntity<EtablissementDto> lire() {
    return reponse(service.lire());
  }

  @PutMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Modifie le profil (If-Match : version lue)")
  ResponseEntity<EtablissementDto> modifier(
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ModifierEtablissementRequete requete) {
    return reponse(
        service.modifier(
            Reponses.version(ifMatch),
            new Modification(
                requete.nom(),
                requete.code(),
                requete.statut(),
                requete.ia(),
                requete.ief(),
                requete.adresse(),
                requete.ville(),
                requete.phraseAccueil(),
                requete.telephone(),
                requete.email(),
                requete.fuseau(),
                requete.devise())));
  }

  @PutMapping(
      path = "/images/{type}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Téléverse le logo, le cachet ou la signature (PNG ou JPEG, 2000 px au plus)")
  ResponseEntity<EtablissementDto> deposerImage(
      @PathVariable TypeImage type, @RequestPart("fichier") MultipartFile fichier)
      throws IOException {
    return reponse(service.deposerImage(type, fichier.getBytes()));
  }

  @DeleteMapping(path = "/images/{type}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Supprime le logo, le cachet ou la signature")
  ResponseEntity<EtablissementDto> supprimerImage(@PathVariable TypeImage type) {
    return reponse(service.supprimerImage(type));
  }

  private ResponseEntity<EtablissementDto> reponse(VueEtablissement vue) {
    return Reponses.ok(mapper.versDto(vue), vue.etablissement().getVersion());
  }
}
