package sn.ecole.personnel.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import sn.ecole.shared.domain.EntiteAuditee;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/** Erreurs et contrôles communs aux services du module. */
final class Erreurs {

  private Erreurs() {}

  static ErreurMetier introuvable(String code, String message) {
    return new ErreurMetier(code, TypeErreur.INTROUVABLE, message);
  }

  static ErreurMetier invalide(String code, String message) {
    return new ErreurMetier(code, TypeErreur.INVALIDE, message);
  }

  static ErreurMetier conflit(String code, String message) {
    return new ErreurMetier(code, TypeErreur.CONFLIT, message);
  }

  /** Verrou optimiste explicite : la version vue par le client doit être la version courante. */
  static void verifierVersion(int attendue, EntiteAuditee entite) {
    if (!Objects.equals(attendue, entite.getVersion())) {
      throw conflit(
          "CONFLIT_VERSION",
          "La donnée a été modifiée entre-temps : rechargez-la puis recommencez.");
    }
  }

  static UUID utilisateurCourant() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      throw new IllegalStateException("Aucun utilisateur authentifié.");
    }
    return UUID.fromString(auth.getName());
  }
}
