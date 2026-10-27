package sn.ecole.shared.api;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import sn.ecole.shared.domain.ErreurMetier;

/** Toutes les erreurs de l'API en ProblemDetail (RFC 9457) avec un code métier. */
@RestControllerAdvice
class GestionnaireErreurs extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GestionnaireErreurs.class);

  @ExceptionHandler(ErreurMetier.class)
  ResponseEntity<ProblemDetail> erreurMetier(ErreurMetier e) {
    ProblemDetail probleme = Problemes.probleme(e);
    return ResponseEntity.status(probleme.getStatus()).body(probleme);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> accesRefuse(AccessDeniedException e) {
    return reponse(HttpStatus.FORBIDDEN, "ACCES_REFUSE", "Action non autorisée.");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> conflitVersion(OptimisticLockingFailureException e) {
    return reponse(
        HttpStatus.CONFLICT,
        "CONFLIT_VERSION",
        "La donnée a été modifiée entre-temps : rechargez-la puis recommencez.");
  }

  /** Contrainte de la base (unicité, clé étrangère) franchie malgré les contrôles applicatifs. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflitDonnees(DataIntegrityViolationException e) {
    LOG.warn("Contrainte de base violée : {}", e.getMostSpecificCause().getMessage());
    return reponse(
        HttpStatus.CONFLICT,
        "CONFLIT_DONNEES",
        "Cette opération entre en conflit avec des données existantes.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> inattendue(Exception e) {
    LOG.error("Erreur inattendue", e);
    return reponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE", "Une erreur interne est survenue.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders entetes,
      HttpStatusCode statut,
      WebRequest requete) {
    ProblemDetail probleme =
        Problemes.probleme(HttpStatus.BAD_REQUEST, "VALIDATION", "Données invalides.");
    List<Map<String, String>> erreurs =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of(
                        "champ",
                        f.getField(),
                        "message",
                        f.getDefaultMessage() == null ? "invalide" : f.getDefaultMessage()))
            .toList();
    probleme.setProperty("erreurs", erreurs);
    return ResponseEntity.badRequest().body(probleme);
  }

  /** Erreurs Spring MVC standard (404, 405, 415…) : on ajoute un code métier générique. */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object corps, HttpHeaders entetes, HttpStatusCode statut, WebRequest requete) {
    if (corps instanceof ProblemDetail probleme
        && (probleme.getProperties() == null || !probleme.getProperties().containsKey("code"))) {
      probleme.setProperty("code", codeGenerique(statut));
    }
    return super.createResponseEntity(corps, entetes, statut, requete);
  }

  private static String codeGenerique(HttpStatusCode statut) {
    return switch (statut.value()) {
      case 400 -> "REQUETE_INVALIDE";
      case 404 -> "RESSOURCE_INTROUVABLE";
      case 405 -> "METHODE_NON_AUTORISEE";
      case 406 -> "FORMAT_NON_ACCEPTABLE";
      case 415 -> "FORMAT_NON_SUPPORTE";
      default -> "ERREUR_HTTP_" + statut.value();
    };
  }

  private static ResponseEntity<ProblemDetail> reponse(
      HttpStatus statut, String code, String detail) {
    return ResponseEntity.status(statut).body(Problemes.probleme(statut, code, detail));
  }
}
