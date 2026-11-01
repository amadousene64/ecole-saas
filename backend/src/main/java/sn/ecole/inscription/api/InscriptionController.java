package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import sn.ecole.inscription.application.ActivationService;
import sn.ecole.inscription.application.ActivationService.CodeEmis;
import sn.ecole.inscription.application.EleveService;
import sn.ecole.inscription.application.InscriptionService;
import sn.ecole.inscription.application.InscriptionService.VueInscription;

/** Inscriptions annuelles, élèves d'une classe, compte élève. */
@RestController
class InscriptionController {

  private final InscriptionService service;
  private final ActivationService activation;
  private final EleveService eleves;
  private final InscriptionMapper mapper;
  private final EleveMapper eleveMapper;

  InscriptionController(
      InscriptionService service,
      ActivationService activation,
      EleveService eleves,
      InscriptionMapper mapper,
      EleveMapper eleveMapper) {
    this.service = service;
    this.activation = activation;
    this.eleves = eleves;
    this.mapper = mapper;
    this.eleveMapper = eleveMapper;
  }

  @PostMapping(
      path = "/api/v1/inscriptions",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Inscrit (ou pré-inscrit) un élève dans une classe d'une année non clôturée ; capacité"
              + " contrôlée")
  ResponseEntity<InscriptionDto> inscrire(@Valid @RequestBody InscriptionRequete r) {
    VueInscription vue =
        service.inscrire(r.eleveId(), r.classeId(), r.statut(), r.redoublant(), r.optionIds());
    return Reponses.cree(
        "/api/v1/inscriptions/" + vue.inscription().getId(),
        mapper.versDto(vue),
        vue.inscription().getVersion());
  }

  @GetMapping(path = "/api/v1/inscriptions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Une inscription")
  ResponseEntity<InscriptionDto> lire(@PathVariable UUID id) {
    return reponse(service.lire(id));
  }

  @PutMapping(
      path = "/api/v1/inscriptions/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Change de classe (même année), redoublement, options (If-Match)")
  ResponseEntity<InscriptionDto> modifier(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody ModificationInscriptionRequete r) {
    return reponse(
        service.modifier(
            id, Reponses.version(ifMatch), r.classeId(), r.redoublant(), r.optionIds()));
  }

  @PostMapping(
      path = "/api/v1/inscriptions/{id}/validation",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Valide une pré-inscription (If-Match)")
  ResponseEntity<InscriptionDto> valider(
      @PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
    return reponse(service.valider(id, Reponses.version(ifMatch)));
  }

  @PostMapping(
      path = "/api/v1/inscriptions/{id}/sortie",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Transfert, abandon, ou radiation (directeur seul) ; définitif (If-Match)")
  ResponseEntity<InscriptionDto> sortir(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody SortieRequete r) {
    return reponse(service.sortir(id, Reponses.version(ifMatch), r.statut(), r.date(), r.motif()));
  }

  @GetMapping(
      path = "/api/v1/eleves/{eleveId}/inscriptions",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Scolarité de l'élève : ses inscriptions, de la plus récente à la plus ancienne")
  ResponseEntity<List<InscriptionDto>> historique(@PathVariable UUID eleveId) {
    return Reponses.ok(mapper.versDtos(service.historique(eleveId)));
  }

  @GetMapping(
      path = "/api/v1/classes/{classeId}/eleves",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Élèves pré-inscrits ou inscrits dans la classe")
  ResponseEntity<List<EleveResumeDto>> elevesDeLaClasse(@PathVariable UUID classeId) {
    return Reponses.ok(eleveMapper.versResumes(service.elevesDeLaClasse(classeId)));
  }

  @PostMapping(
      path = "/api/v1/eleves/{eleveId}/codes-activation",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Émet un code d'activation du compte élève (collège et lycée seulement) ; affiché une"
              + " seule fois")
  ResponseEntity<CodeActivationDto> emettreCodeEleve(@PathVariable UUID eleveId) {
    CodeEmis code = activation.emettrePourEleve(eleveId);
    return Reponses.cree(
        "/api/v1/eleves/" + eleveId, new CodeActivationDto(code.code(), code.expireLe()));
  }

  @GetMapping(path = "/api/v1/moi/eleve", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "L'élève connecté : sa propre fiche d'identité")
  ResponseEntity<EleveDto> moi() {
    EleveService.VueEleve vue = eleves.moi();
    return Reponses.ok(eleveMapper.versDto(vue), vue.eleve().getVersion());
  }

  private ResponseEntity<InscriptionDto> reponse(VueInscription vue) {
    return Reponses.ok(mapper.versDto(vue), vue.inscription().getVersion());
  }
}
