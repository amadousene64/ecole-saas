package sn.ecole.inscription.application;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.EleveRepository;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.inscription.domain.LienParentalRepository;
import sn.ecole.inscription.domain.ResponsableRepository;
import sn.ecole.shared.application.VerificateurPerimetre;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Périmètre {@code hasPermission(#eleveId, 'Eleve', 'lire')} :
 *
 * <ul>
 *   <li>un PARENT ne voit que les élèves auxquels son responsable est lié, et seulement si ce lien
 *       lui donne accès à l'espace parent ;
 *   <li>un ÉLÈVE ne voit que lui-même.
 * </ul>
 *
 * Le personnel passe par ses rôles, jamais par ce vérificateur. Refus par défaut (identifiant mal
 * formé, rôle absent, élève d'une autre école : les lectures sont limitées à l'école courante).
 */
@Component
class PerimetreEleve implements VerificateurPerimetre {

  static final String TYPE = "Eleve";

  private final EleveRepository eleves;
  private final ResponsableRepository responsables;
  private final LienParentalRepository liens;

  PerimetreEleve(
      EleveRepository eleves, ResponsableRepository responsables, LienParentalRepository liens) {
    this.eleves = eleves;
    this.responsables = responsables;
    this.liens = liens;
  }

  @Override
  public String typeCible() {
    return TYPE;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean autorise(Authentication utilisateur, Serializable idCible, String permission) {
    if (!"lire".equals(permission) || !(idCible instanceof UUID eleveId)) {
      return false;
    }
    UUID moi;
    try {
      moi = UUID.fromString(utilisateur.getName());
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (aLeRole(utilisateur, RoleEcole.PARENT) && parentLie(moi, eleveId)) {
      return true;
    }
    return aLeRole(utilisateur, RoleEcole.ELEVE)
        && eleves.findById(eleveId).map(e -> moi.equals(e.getUtilisateurId())).orElse(false);
  }

  private boolean parentLie(UUID moi, UUID eleveId) {
    return responsables
        .findByUtilisateurId(moi)
        .flatMap(r -> liens.findByEleveIdAndResponsableId(eleveId, r.getId()))
        .map(LienParental::isAccesEspaceParent)
        .orElse(false);
  }

  static boolean aLeRole(Authentication utilisateur, RoleEcole role) {
    return utilisateur.getAuthorities().stream()
        .anyMatch(a -> role.autorite().equals(a.getAuthority()));
  }
}
