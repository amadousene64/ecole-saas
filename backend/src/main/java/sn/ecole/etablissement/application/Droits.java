package sn.ecole.etablissement.application;

/**
 * Expressions d'autorisation du module. Les rôles viennent de la table {@code appartenance}, pour
 * l'école courante seulement (voir {@code FiltreTenant}).
 */
final class Droits {

  /** Tous les rôles d'une école, y compris parents et élèves. */
  static final String LECTURE_TOUS =
      "hasAnyRole('DIRECTEUR','CENSEUR','SECRETARIAT','SURVEILLANT','ENSEIGNANT','COMPTABLE',"
          + "'PARENT','ELEVE')";

  /** Le personnel de l'école : tous les rôles sauf PARENT et ELEVE. */
  static final String LECTURE_PERSONNEL =
      "hasAnyRole('DIRECTEUR','CENSEUR','SECRETARIAT','SURVEILLANT','ENSEIGNANT','COMPTABLE')";

  static final String ECRITURE = "hasAnyRole('DIRECTEUR','SECRETARIAT')";

  /**
   * Organisation des enseignements (matières, grille horaire, puis emploi du temps) : la direction,
   * le censeur, qui fait d'ordinaire l'emploi du temps, et le secrétariat pour la saisie.
   */
  static final String PEDAGOGIE = "hasAnyRole('DIRECTEUR','CENSEUR','SECRETARIAT')";

  /** Actions sensibles ou irréversibles : cachet, signature, ouverture et clôture, cycles. */
  static final String DIRECTION = "hasRole('DIRECTEUR')";

  private Droits() {}
}
