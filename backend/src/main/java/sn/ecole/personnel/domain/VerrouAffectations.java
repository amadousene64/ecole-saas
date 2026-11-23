package sn.ecole.personnel.domain;

/**
 * Verrou de transaction sur une « place » d'affectation (une classe, ou un surveillant) : deux
 * créations ou modifications simultanées se suivent, et le contrôle de chevauchement voit la
 * première avant d'accepter la seconde.
 */
public interface VerrouAffectations {

  /** Bloque jusqu'à la fin de la transaction courante ; propre à l'école courante. */
  void verrouiller(String place);
}
