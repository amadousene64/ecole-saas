package sn.ecole.inscription.api;

import java.time.LocalDate;
import java.util.UUID;
import sn.ecole.inscription.domain.Sexe;

/** Ligne de la liste des élèves. */
public record EleveResumeDto(
    UUID id, String matricule, String prenoms, String nom, Sexe sexe, LocalDate dateNaissance) {

  @Override
  public String toString() {
    return "EleveResumeDto[" + id + "]";
  }
}
