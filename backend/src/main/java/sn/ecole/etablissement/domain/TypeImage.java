package sn.ecole.etablissement.domain;

/** Images officielles de l'établissement, stockées dans MinIO. */
public enum TypeImage {
  LOGO(1_000_000),
  CACHET(1_000_000),
  SIGNATURE(512_000);

  private final int tailleMax;

  TypeImage(int tailleMax) {
    this.tailleMax = tailleMax;
  }

  /** Taille maximale du fichier déposé, en octets. */
  public int tailleMax() {
    return tailleMax;
  }

  /** Le cachet et la signature engagent le chef d'établissement : lecture et dépôt réservés. */
  public boolean sensible() {
    return this != LOGO;
  }
}
