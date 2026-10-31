package sn.ecole.inscription.api;

import jakarta.validation.constraints.Size;

/**
 * Modification partielle des données sensibles : un champ absent ({@code null}) est inchangé, une
 * chaîne vide l'efface.
 */
public record DonneesSensiblesRequete(
    @Size(max = 2000) String sante,
    @Size(max = 1000) String allergies,
    @Size(max = 1000) String situationFamiliale) {

  @Override
  public String toString() {
    return "DonneesSensiblesRequete[***]";
  }
}
