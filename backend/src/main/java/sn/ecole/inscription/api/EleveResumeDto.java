package sn.ecole.inscription.api;

import java.time.LocalDate;
import java.util.UUID;
import sn.ecole.inscription.domain.Sexe;

/** Ligne d'une liste d'élèves ; {@code classe} : classe actuelle, absente si non inscrit. */
public record EleveResumeDto(
    UUID id,
    String matricule,
    String prenoms,
    String nom,
    Sexe sexe,
    LocalDate dateNaissance,
    String classe) {

  @Override
  public String toString() {
    return "EleveResumeDto[" + id + "]";
  }
}
