package sn.ecole.etablissement.api;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/**
 * ETag et If-Match : le client renvoie la version qu'il a lue, le service refuse si elle a changé.
 */
final class Reponses {

  private Reponses() {}

  static <T> ResponseEntity<T> ok(T corps, Integer version) {
    return ResponseEntity.ok().eTag(etag(version)).body(corps);
  }

  static <T> ResponseEntity<T> cree(String chemin, T corps, Integer version) {
    return ResponseEntity.created(URI.create(chemin)).eTag(etag(version)).body(corps);
  }

  /** « "3" » ou « W/"3" » devient 3. */
  static int version(String ifMatch) {
    String brut = ifMatch == null ? "" : ifMatch.strip();
    if (brut.startsWith("W/")) {
      brut = brut.substring(2);
    }
    brut = brut.replace("\"", "");
    try {
      return Integer.parseInt(brut);
    } catch (NumberFormatException e) {
      throw new ErreurMetier(
          "IF_MATCH_INVALIDE",
          TypeErreur.INVALIDE,
          "L'en-tête If-Match doit reprendre l'ETag reçu à la lecture.");
    }
  }

  private static String etag(Integer version) {
    return "\"" + version + "\"";
  }
}
