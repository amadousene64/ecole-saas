package sn.ecole.inscription.api;

/**
 * Contenu en clair des données sensibles (direction et censeur seulement ; lecture journalisée).
 */
public record DonneesSensiblesDto(String sante, String allergies, String situationFamiliale) {

  @Override
  public String toString() {
    return "DonneesSensiblesDto[***]";
  }
}
