package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Code remis par l'école, sous la forme XXXX-XXXX-XXXX (tirets et casse indifférents). */
public record ActivationRequete(@NotBlank @Size(max = 40) String code) {

  @Override
  public String toString() {
    return "ActivationRequete[***]";
  }
}
