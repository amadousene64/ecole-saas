package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import sn.ecole.inscription.domain.Sexe;

/** Identité d'un élève. La nationalité est un code pays ISO à deux lettres (SN, GN, ML…). */
public record EleveRequete(
    @NotBlank @Size(max = 100) String prenoms,
    @NotBlank @Size(max = 60) String nom,
    @NotNull Sexe sexe,
    @NotNull LocalDate dateNaissance,
    @NotBlank @Size(max = 100) String lieuNaissance,
    @NotBlank @Size(min = 2, max = 2) String nationalite,
    @Size(max = 30) String identifiantNational) {

  @Override
  public String toString() {
    return "EleveRequete[***]";
  }
}
