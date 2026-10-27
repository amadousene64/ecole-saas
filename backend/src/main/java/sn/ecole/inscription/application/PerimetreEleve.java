package sn.ecole.inscription.application;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.EleveRepository;
import sn.ecole.shared.application.VerificateurPerimetre;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Périmètre {@code hasPermission(#eleveId, 'Eleve', 'lire')} : un ÉLÈVE ne voit que lui-même. Le
 * personnel passe par ses rôles, jamais par ce vérificateur. Refus par défaut (identifiant mal
 * formé, rôle absent, élève d'une autre école : la lecture est limitée à l'école courante).
 */
@Component
class PerimetreEleve implements VerificateurPerimetre {

  static final String TYPE = "Eleve";

  private final EleveRepository eleves;

  PerimetreEleve(EleveRepository eleves) {
    this.eleves = eleves;
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
    return aLeRole(utilisateur, RoleEcole.ELEVE)
        && eleves.findById(eleveId).map(e -> moi.equals(e.getUtilisateurId())).orElse(false);
  }

  static boolean aLeRole(Authentication utilisateur, RoleEcole role) {
    return utilisateur.getAuthorities().stream()
        .anyMatch(a -> role.autorite().equals(a.getAuthority()));
  }
}
