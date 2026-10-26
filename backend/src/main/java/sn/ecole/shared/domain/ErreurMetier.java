package sn.ecole.shared.domain;

import java.util.Objects;

/**
 * Erreur métier portant un code stable (ex. {@code BILLET_EXPIRE}), renvoyée au client sous forme
 * de ProblemDetail (RFC 9457) avec la propriété {@code code}.
 */
public class ErreurMetier extends RuntimeException {

  private final String code;
  private final TypeErreur type;

  public ErreurMetier(String code, TypeErreur type, String message) {
    super(message);
    this.code = Objects.requireNonNull(code);
    this.type = Objects.requireNonNull(type);
  }

  public String code() {
    return code;
  }

  public TypeErreur type() {
    return type;
  }
}
