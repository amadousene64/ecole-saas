package sn.ecole.inscription.api;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import sn.ecole.inscription.domain.StatutInscription;

/**
 * Recherche d'élèves par nom : le terme voyage dans le corps, jamais dans l'URL (les noms de
 * mineurs n'ont rien à faire dans un journal d'accès). Tous les champs sont facultatifs.
 */
public record RechercheElevesRequete(
    @Size(max = 100) String recherche,
    UUID classe,
    StatutInscription statut,
    @Size(max = 100) String curseur,
    Integer taille) {

  @Override
  public String toString() {
    return "RechercheElevesRequete[***]";
  }
}
