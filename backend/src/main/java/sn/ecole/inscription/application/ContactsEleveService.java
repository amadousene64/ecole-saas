package sn.ecole.inscription.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.ContactsEleve;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.inscription.domain.LienParentalRepository;
import sn.ecole.inscription.domain.ResponsableRepository;

/**
 * Implémentation de {@link ContactsEleve}. Appelée par d'autres modules dans l'école courante, à
 * partir d'un cas d'usage qui a déjà contrôlé ses propres droits (ou d'un traitement de fond) : pas
 * de {@code @PreAuthorize} ici.
 */
@Service
@Transactional(readOnly = true)
public class ContactsEleveService implements ContactsEleve {

  private final ResponsableRepository responsables;
  private final LienParentalRepository liens;

  public ContactsEleveService(ResponsableRepository responsables, LienParentalRepository liens) {
    this.responsables = responsables;
    this.liens = liens;
  }

  @Override
  public boolean estParentAutorise(UUID utilisateurId, UUID eleveId) {
    return responsables
        .findByUtilisateurId(utilisateurId)
        .flatMap(r -> liens.findByEleveIdAndResponsableId(eleveId, r.getId()))
        .map(LienParental::isAccesEspaceParent)
        .orElse(false);
  }

  @Override
  public List<Destinataire> destinatairesNotifications(UUID eleveId) {
    List<LienParental> notifies =
        liens.findByEleveId(eleveId).stream().filter(LienParental::notifie).toList();
    return responsables
        .findByIdIn(notifies.stream().map(LienParental::getResponsableId).toList())
        .stream()
        .map(r -> new Destinataire(r.getId(), r.getUtilisateurId(), r.getTelephone()))
        .toList();
  }
}
