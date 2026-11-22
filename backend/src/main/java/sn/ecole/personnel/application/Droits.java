package sn.ecole.personnel.application;

/** Expressions d'autorisation du module ; rôles de l'école courante seulement. */
final class Droits {

  /** Affectations et annuaire du personnel : la direction et le censeur. */
  static final String GESTION = "hasAnyRole('DIRECTEUR','CENSEUR')";

  /** Ses propres affectations en cours (« Mes classes »). */
  static final String MES_AFFECTATIONS = "hasAnyRole('ENSEIGNANT','SURVEILLANT')";

  private Droits() {}
}
