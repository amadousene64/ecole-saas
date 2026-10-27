package sn.ecole.etablissement.application;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import sn.ecole.shared.domain.EntiteAuditee;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.TypeErreur;

/** Erreurs et contrôles communs aux services du module. */
final class Erreurs {

  private Erreurs() {}

  static ErreurMetier introuvable(String code, String message) {
    return new ErreurMetier(code, TypeErreur.INTROUVABLE, message);
  }

  static ErreurMetier conflit(String code, String message) {
    return new ErreurMetier(code, TypeErreur.CONFLIT, message);
  }

  static ErreurMetier regle(String code, String message) {
    return new ErreurMetier(code, TypeErreur.REGLE_METIER, message);
  }

  /** Verrou optimiste explicite : la version vue par le client doit être la version courante. */
  static void verifierVersion(int attendue, EntiteAuditee entite) {
    if (!Objects.equals(attendue, entite.getVersion())) {
      throw conflit(
          "CONFLIT_VERSION",
          "La donnée a été modifiée entre-temps : rechargez-la puis recommencez.");
    }
  }

  static boolean estDirecteur() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.getAuthorities().stream()
            .anyMatch(a -> RoleEcole.DIRECTEUR.autorite().equals(a.getAuthority()));
  }

  /** Pour les champs réservés au directeur dans une opération ouverte au secrétariat. */
  static void exigerDirecteur() {
    if (!estDirecteur()) {
      throw new AccessDeniedException("Réservé au directeur.");
    }
  }
}
