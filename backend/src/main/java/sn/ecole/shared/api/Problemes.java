package sn.ecole.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;
import tools.jackson.databind.json.JsonMapper;

/** Fabrique des réponses RFC 9457 portant un code métier (propriété {@code code}). */
@Component
public class Problemes {

  private final JsonMapper json;

  public Problemes(JsonMapper json) {
    this.json = json;
  }

  public static HttpStatus statut(TypeErreur type) {
    return switch (type) {
      case INVALIDE -> HttpStatus.BAD_REQUEST;
      case NON_AUTHENTIFIE -> HttpStatus.UNAUTHORIZED;
      case INTERDIT -> HttpStatus.FORBIDDEN;
      case INTROUVABLE -> HttpStatus.NOT_FOUND;
      case CONFLIT -> HttpStatus.CONFLICT;
      case REGLE_METIER -> HttpStatus.UNPROCESSABLE_CONTENT;
    };
  }

  public static ProblemDetail probleme(HttpStatus statut, String code, String detail) {
    ProblemDetail probleme = ProblemDetail.forStatusAndDetail(statut, detail);
    probleme.setType(URI.create("urn:ecole:probleme:" + code));
    probleme.setTitle(statut.getReasonPhrase());
    probleme.setProperty("code", code);
    return probleme;
  }

  public static ProblemDetail probleme(ErreurMetier erreur) {
    return probleme(statut(erreur.type()), erreur.code(), erreur.getMessage());
  }

  /** Écriture directe, pour les erreurs levées dans un filtre (hors contrôleur). */
  void ecrire(HttpServletRequest requete, HttpServletResponse reponse, ErreurMetier erreur)
      throws IOException {
    ProblemDetail probleme = probleme(erreur);
    probleme.setInstance(URI.create(requete.getRequestURI()));
    reponse.setStatus(probleme.getStatus());
    reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    reponse.setCharacterEncoding("UTF-8");
    json.writeValue(reponse.getOutputStream(), probleme);
  }
}
