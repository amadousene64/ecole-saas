package sn.ecole.inscription.application;

/**
 * Expressions d'autorisation du module. Les rôles viennent de la table {@code appartenance}, pour
 * l'école courante seulement. Un parent ou un élève n'accède à un élève que par {@code
 * hasPermission(#id, 'Eleve', 'lire')} (voir {@link PerimetreEleve}).
 *
 * <ul>
 *   <li>DIRECTEUR et SECRETARIAT écrivent ;
 *   <li>CENSEUR et SURVEILLANT lisent l'identité et la scolarité ;
 *   <li>COMPTABLE lit l'identité et les responsables payeurs ;
 *   <li>ENSEIGNANT : aucun accès pour l'instant (issue « accès enseignant limité à ses classes »).
 * </ul>
 */
final class Droits {

  static final String ECRITURE = "hasAnyRole('DIRECTEUR','SECRETARIAT')";

  static final String DIRECTION = "hasRole('DIRECTEUR')";

  /** Identité : le personnel concerné, sans l'enseignant. */
  static final String IDENTITE =
      "hasAnyRole('DIRECTEUR','SECRETARIAT','CENSEUR','SURVEILLANT','COMPTABLE')";

  /** Identité d'un élève donné : le personnel, son parent lié, lui-même. */
  static final String IDENTITE_ELEVE = IDENTITE + " or hasPermission(#eleveId, 'Eleve', 'lire')";

  /** Scolarité (inscriptions, classes) : sans le comptable. */
  static final String SCOLARITE = "hasAnyRole('DIRECTEUR','SECRETARIAT','CENSEUR','SURVEILLANT')";

  static final String SCOLARITE_ELEVE = SCOLARITE + " or hasPermission(#eleveId, 'Eleve', 'lire')";

  /** Lecture des données de santé et de la situation familiale. */
  static final String SENSIBLE_LECTURE = "hasAnyRole('DIRECTEUR','CENSEUR')";

  /** Saisie des données sensibles : le secrétariat écrit sans pouvoir relire. */
  static final String SENSIBLE_ECRITURE = "hasAnyRole('DIRECTEUR','SECRETARIAT')";

  /**
   * Pièces du dossier : la direction et le secrétariat, plus le censeur pour le seul certificat
   * médical (donnée de santé). Le détail par type est vérifié dans {@link PieceService}.
   */
  static final String PIECES_LECTURE = "hasAnyRole('DIRECTEUR','SECRETARIAT','CENSEUR')";

  /** Savoir si une donnée sensible est renseignée, sans la lire. */
  static final String SENSIBLE_ETAT = "hasAnyRole('DIRECTEUR','SECRETARIAT','CENSEUR')";

  private Droits() {}
}
