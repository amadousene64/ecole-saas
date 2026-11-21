package sn.ecole.inscription.api;

import java.util.UUID;

/**
 * Responsable trouvé par son téléphone, pour éviter un doublon à la saisie : de quoi le reconnaître
 * et le lier, rien de plus (ni téléphone, ni courriel, ni profession).
 */
public record ResponsableResumeDto(UUID id, String prenoms, String nom) {

  @Override
  public String toString() {
    return "ResponsableResumeDto[" + id + "]";
  }
}
