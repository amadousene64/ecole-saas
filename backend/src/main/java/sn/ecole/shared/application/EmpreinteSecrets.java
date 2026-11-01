package sn.ecole.shared.application;

/**
 * Empreinte (HMAC) d'un secret court, comme un code d'activation : la base ne contient que
 * l'empreinte, et la clé, hors base, empêche de retrouver le code par force brute à partir d'une
 * copie de la base. Clé distincte de celle du chiffrement des données.
 */
public interface EmpreinteSecrets {

  byte[] empreinte(String secret, String contexte);

  /** Comparaison en temps constant. */
  boolean correspond(String secret, String contexte, byte[] empreinteAttendue);
}
