package sn.ecole.shared.application;

/**
 * Chiffrement en colonne des données sensibles (santé, situation familiale). Chiffrement
 * authentifié : le {@code contexte} (école, ligne, champ) est lié au chiffré, qui ne peut donc pas
 * être recopié vers une autre ligne, un autre champ ou une autre école sans que le déchiffrement
 * échoue. La clé vit hors de la base.
 */
public interface ChiffrementDonnees {

  /** {@code null} reste {@code null}. */
  byte[] chiffrer(String clair, String contexte);

  /**
   * {@code null} reste {@code null}.
   *
   * @throws IllegalStateException si le chiffré a été altéré, déplacé, ou si sa clé est inconnue
   */
  String dechiffrer(byte[] chiffre, String contexte);
}
