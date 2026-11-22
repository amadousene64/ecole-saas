package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.ecole.inscription.api.BrouillonInscriptionRequetes.BrouillonFinaliseDto;
import sn.ecole.inscription.api.BrouillonInscriptionRequetes.BrouillonResumeDto;
import sn.ecole.inscription.api.BrouillonInscriptionRequetes.EnregistrementBrouillon;
import sn.ecole.inscription.api.BrouillonInscriptionRequetes.FinalisationBrouillon;
import sn.ecole.inscription.api.BrouillonInscriptionRequetes.OuvertureBrouillon;
import sn.ecole.inscription.application.BrouillonInscriptionService;
import sn.ecole.inscription.application.BrouillonInscriptionService.Finalisation;
import sn.ecole.inscription.application.BrouillonInscriptionService.Resume;
import sn.ecole.inscription.application.BrouillonInscriptionService.Vue;
import sn.ecole.inscription.application.PieceService;
import sn.ecole.inscription.domain.PieceDossier;
import sn.ecole.shared.domain.PageCurseur;

/**
 * Assistant d'inscription : brouillon enregistré côté serveur (contenu chiffré), pièces rattachées
 * au brouillon, finalisation en une transaction. Direction et secrétariat.
 */
@RestController
@RequestMapping("/api/v1/brouillons-inscription")
class BrouillonInscriptionController {

  private static final String CHEMIN = "/api/v1/brouillons-inscription/";

  private final BrouillonInscriptionService service;
  private final PieceService pieces;
  private final BrouillonInscriptionMapper mapper;

  BrouillonInscriptionController(
      BrouillonInscriptionService service, PieceService pieces, BrouillonInscriptionMapper mapper) {
    this.service = service;
    this.pieces = pieces;
    this.mapper = mapper;
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Ouvre un brouillon vide (année en cours, sauf année donnée)")
  ResponseEntity<BrouillonInscriptionDto> ouvrir(
      @RequestBody(required = false) OuvertureBrouillon requete) {
    Vue vue = service.ouvrir(requete == null ? null : requete.anneeId());
    return Reponses.cree(CHEMIN + vue.id(), mapper.versDto(vue), vue.version());
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Brouillons en cours de l'école (nom de l'élève, étape, date de suppression), du plus"
              + " récent au plus ancien ; paginé par curseur")
  ResponseEntity<PageDto<BrouillonResumeDto>> lister(
      @RequestParam(name = "curseur", required = false) String curseur,
      @RequestParam(name = "taille", required = false) Integer taille) {
    PageCurseur<Resume> page = service.lister(curseur, taille);
    return Reponses.ok(new PageDto<>(mapper.versResumes(page.elements()), page.curseurSuivant()));
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Brouillon déchiffré ; ETag = version")
  ResponseEntity<BrouillonInscriptionDto> lire(@PathVariable UUID id) {
    Vue vue = service.lire(id);
    return Reponses.ok(mapper.versDto(vue), vue.version());
  }

  @PutMapping(
      path = "/{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Enregistre une étape (contenu complet) ; If-Match : version lue")
  ResponseEntity<BrouillonInscriptionDto> enregistrer(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody EnregistrementBrouillon requete) {
    Vue vue =
        service.enregistrer(
            id, Reponses.version(ifMatch), requete.etape(), mapper.versContenu(requete.contenu()));
    return Reponses.ok(mapper.versDto(vue), vue.version());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Abandonne le brouillon : contenu effacé, pièces supprimées")
  void abandonner(@PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
    service.abandonner(id, Reponses.version(ifMatch));
  }

  @PostMapping(
      path = "/{id}/finalisation",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Crée l'élève, ses responsables et l'inscription, rattache les pièces et clôt le"
              + " brouillon (tout ou rien) ; If-Match : version lue")
  ResponseEntity<BrouillonFinaliseDto> finaliser(
      @PathVariable UUID id,
      @RequestHeader("If-Match") String ifMatch,
      @Valid @RequestBody FinalisationBrouillon requete) {
    Finalisation resultat =
        service.finaliser(id, Reponses.version(ifMatch), requete.statut(), requete.codePour());
    return Reponses.cree("/api/v1/eleves/" + resultat.eleveId(), mapper.versDto(resultat));
  }

  @PostMapping(
      path = "/{id}/pieces",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Dépose une pièce dans le brouillon (PDF, JPEG ou PNG, 5 Mo au plus), analysée par"
              + " antivirus ; rattachée à l'élève à la finalisation")
  ResponseEntity<PieceDto> deposer(
      @PathVariable UUID id,
      @RequestPart("type") String type,
      @RequestPart("fichier") MultipartFile fichier)
      throws IOException {
    PieceDossier piece =
        pieces.deposerDansBrouillon(id, PieceController.type(type), fichier.getBytes());
    return Reponses.cree(CHEMIN + id + "/pieces/" + piece.getId(), PieceController.versDto(piece));
  }

  @GetMapping(path = "/{id}/pieces", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Pièces déposées dans le brouillon, avec leur statut d'analyse")
  ResponseEntity<List<PieceDto>> pieces(@PathVariable UUID id) {
    return Reponses.ok(
        pieces.listerDuBrouillon(id).stream().map(PieceController::versDto).toList());
  }

  @DeleteMapping("/{id}/pieces/{pieceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Retire une pièce du brouillon ; le fichier est effacé du stockage")
  void retirerPiece(@PathVariable UUID id, @PathVariable UUID pieceId) {
    pieces.supprimerDuBrouillon(id, pieceId);
  }
}
