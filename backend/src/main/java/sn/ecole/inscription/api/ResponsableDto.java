package sn.ecole.inscription.api;

import java.util.UUID;

/** {@code compteActive} : un compte parent est relié (son identifiant n'est jamais exposé). */
public record ResponsableDto(
    UUID id,
    Integer version,
    String prenoms,
    String nom,
    String telephone,
    String email,
    String profession,
    boolean compteActive) {

  @Override
  public String toString() {
    return "ResponsableDto[" + id + "]";
  }
}
