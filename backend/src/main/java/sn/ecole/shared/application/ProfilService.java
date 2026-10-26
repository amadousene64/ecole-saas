package sn.ecole.shared.application;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.EcoleUtilisateur;
import sn.ecole.shared.domain.EcolesUtilisateurRepository;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.TypeErreur;

@Service
public class ProfilService {

  private final EcolesUtilisateurRepository ecoles;

  public ProfilService(EcolesUtilisateurRepository ecoles) {
    this.ecoles = ecoles;
  }

  /** Profil de l'utilisateur connecté dans l'école courante. */
  @PreAuthorize("isAuthenticated()")
  public ProfilCourant profilCourant() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Set<RoleEcole> roles = EnumSet.noneOf(RoleEcole.class);
    for (GrantedAuthority autorite : auth.getAuthorities()) {
      for (RoleEcole role : RoleEcole.values()) {
        if (role.autorite().equals(autorite.getAuthority())) {
          roles.add(role);
        }
      }
    }
    return new ProfilCourant(UUID.fromString(auth.getName()), ContexteTenant.exiger(), roles);
  }

  /**
   * Écoles de l'utilisateur connecté. L'identifiant vient uniquement du {@code sub} du jeton,
   * jamais d'un paramètre : on ne peut lister que SES écoles.
   */
  @PreAuthorize("isAuthenticated()")
  public List<EcoleUtilisateur> mesEcoles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UUID utilisateurId;
    try {
      utilisateurId = UUID.fromString(Objects.requireNonNull(auth.getName()));
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ErreurMetier(
          "JETON_INVALIDE", TypeErreur.NON_AUTHENTIFIE, "Identifiant d'utilisateur invalide.");
    }
    return ecoles.ecolesDe(utilisateurId);
  }
}
