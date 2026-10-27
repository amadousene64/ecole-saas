package sn.ecole.inscription.api;

import java.time.LocalDate;
import java.util.UUID;
import sn.ecole.inscription.domain.Sexe;

/**
 * Identité d'un élève. {@code photoUrl} : URL signée valable 5 minutes. {@code compteActive} : un
 * compte élève est relié (l'identifiant du compte n'est jamais exposé).
 */
public record EleveDto(
    UUID id,
    Integer version,
    String matricule,
    String prenoms,
    String nom,
    Sexe sexe,
    LocalDate dateNaissance,
    String lieuNaissance,
    String nationalite,
    String identifiantNational,
    String photoUrl,
    boolean compteActive) {

  @Override
  public String toString() {
    return "EleveDto[" + id + "]";
  }
}
