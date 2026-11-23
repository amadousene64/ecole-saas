package sn.ecole.inscription.api;

import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.ecole.inscription.application.PieceService;
import sn.ecole.inscription.domain.PieceDossier;

/** Pièces du dossier de l'élève, analysées par antivirus avant toute mise à disposition. */
@RestController
@RequestMapping("/api/v1/eleves/{eleveId}/pieces")
class PieceController {

  private final PieceService service;
  private final Clock horloge;

  PieceController(PieceService service, Clock horloge) {
    this.horloge = horloge;
    this.service = service;
  }

  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "Dépose une pièce (PDF, JPEG ou PNG, 5 Mo au plus) ; inaccessible jusqu'à l'analyse"
              + " antivirus")
  ResponseEntity<PieceDto> deposer(
      @PathVariable UUID eleveId,
      @RequestPart("type") String type,
      @RequestPart("fichier") MultipartFile fichier)
      throws IOException {
    PieceDossier piece = service.deposer(eleveId, type(type), fichier.getBytes());
    return Reponses.cree("/api/v1/eleves/" + eleveId + "/pieces/" + piece.getId(), versDto(piece));
  }

  /** Type de pièce reçu en partie de formulaire ; partagé avec l'assistant d'inscription. */
  static PieceDossier.Type type(String type) {
    try {
      return PieceDossier.Type.valueOf(type.strip());
    } catch (IllegalArgumentException e) {
      throw new sn.ecole.shared.domain.ErreurMetier(
          "CHAMP_INVALIDE",
          sn.ecole.shared.domain.TypeErreur.INVALIDE,
          "Type de pièce : ACTE_NAISSANCE, CERTIFICAT_SCOLARITE, CERTIFICAT_MEDICAL ou"
              + " PHOTO_IDENTITE.");
    }
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Pièces du dossier, avec leur statut d'analyse")
  ResponseEntity<List<PieceDto>> lister(@PathVariable UUID eleveId) {
    return Reponses.ok(service.lister(eleveId).stream().map(PieceController::versDto).toList());
  }

  @GetMapping(path = "/{pieceId}/url", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "URL de téléchargement (5 minutes), seulement pour une pièce saine ; certificat médical :"
              + " direction et censeur seulement, chaque ouverture journalisée")
  ResponseEntity<UrlDto> url(@PathVariable UUID eleveId, @PathVariable UUID pieceId) {
    return Reponses.ok(
        new UrlDto(
            service.url(eleveId, pieceId).toString(),
            horloge.instant().plus(PieceService.VALIDITE_URL)));
  }

  @DeleteMapping("/{pieceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Supprime (logiquement) une pièce ; le fichier est effacé du stockage")
  void supprimer(@PathVariable UUID eleveId, @PathVariable UUID pieceId) {
    service.supprimer(eleveId, pieceId);
  }

  static PieceDto versDto(PieceDossier p) {
    return new PieceDto(
        p.getId(),
        p.getType(),
        p.getContentType(),
        p.getTaille(),
        p.getStatut(),
        p.getCreeLe(),
        p.getAnalyseLe());
  }
}
