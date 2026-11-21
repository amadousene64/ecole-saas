package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Téléphone à rechercher, dans le CORPS de la requête : jamais dans l'URL, qui finit dans les
 * journaux d'accès (proxy, répartiteur, traces).
 */
public record RechercheResponsableRequete(@NotBlank @Size(max = 30) String telephone) {

  @Override
  public String toString() {
    return "RechercheResponsableRequete[***]";
  }
}
