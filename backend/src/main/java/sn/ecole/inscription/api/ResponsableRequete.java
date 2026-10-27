package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Téléphone obligatoire (normalisé en E.164, +221 par défaut) ; e-mail facultatif. */
public record ResponsableRequete(
    @NotBlank @Size(max = 100) String prenoms,
    @NotBlank @Size(max = 60) String nom,
    @NotBlank @Size(max = 30) String telephone,
    @Size(max = 254) String email,
    @Size(max = 100) String profession) {

  @Override
  public String toString() {
    return "ResponsableRequete[***]";
  }
}
