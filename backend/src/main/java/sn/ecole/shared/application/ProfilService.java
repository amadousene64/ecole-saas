package sn.ecole.shared.application;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.RoleEcole;

@Service
public class ProfilService {

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
}
