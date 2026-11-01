package sn.ecole.inscription;

import java.util.List;
import java.util.UUID;

/**
 * Pour les autres modules (vie scolaire, communication, finance) : qui peut consulter un élève, qui
 * prévenir. Un responsable dont l'accès à l'espace parent est retiré n'apparaît jamais, ni comme
 * parent autorisé ni comme destinataire.
 */
public interface ContactsEleve {

  /** Vrai si l'utilisateur est un parent lié à l'élève avec accès à l'espace parent. */
  boolean estParentAutorise(UUID utilisateurId, UUID eleveId);

  /** Responsables à notifier pour cet élève (accès à l'espace parent et notifications actifs). */
  List<Destinataire> destinatairesNotifications(UUID eleveId);

  /**
   * @param utilisateurId compte relié au responsable, ou null s'il n'a pas encore activé son compte
   * @param telephone E.164, pour le SMS de secours
   */
  record Destinataire(UUID responsableId, UUID utilisateurId, String telephone) {

    @Override
    public String toString() {
      return "Destinataire[" + responsableId + "]";
    }
  }
}
