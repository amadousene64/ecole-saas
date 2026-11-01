package sn.ecole.inscription.api;

import java.time.Instant;

/** Code à remettre au titulaire : affiché une seule fois, jamais relisible ensuite. */
public record CodeActivationDto(String code, Instant expireLe) {

  @Override
  public String toString() {
    return "CodeActivationDto[***]";
  }
}
