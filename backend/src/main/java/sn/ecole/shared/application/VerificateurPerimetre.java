package sn.ecole.shared.application;

import java.io.Serializable;
import org.springframework.security.core.Authentication;

/**
 * Vérifie le périmètre d'un utilisateur sur un type d'objet (ex. : l'enseignant enseigne-t-il dans
 * cette classe ?). Chaque module en fournit un par type, utilisé via {@code hasPermission(#id,
 * "Classe", "lire")} dans {@code @PreAuthorize}.
 */
public interface VerificateurPerimetre {

  /** Nom du type cible, tel qu'écrit dans {@code hasPermission}. */
  String typeCible();

  boolean autorise(Authentication utilisateur, Serializable idCible, String permission);
}
