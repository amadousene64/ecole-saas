package sn.ecole.inscription.domain;

/** Port de l'analyse antivirus (ClamAV). */
public interface AntiVirus {

  /**
   * Résultat : {@code signature} est le nom de la menace trouvée, ou null si le contenu est sain.
   */
  record Resultat(boolean sain, String signature) {}

  /**
   * @throws IllegalStateException si l'analyse est impossible (antivirus injoignable, réponse
   *     inattendue) : la pièce reste EN_ANALYSE et l'analyse sera retentée.
   */
  Resultat analyser(byte[] contenu);
}
