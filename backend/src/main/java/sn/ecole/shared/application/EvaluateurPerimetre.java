package sn.ecole.shared.application;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** {@link PermissionEvaluator} qui délègue aux vérificateurs des modules ; refuse par défaut. */
@Component
public class EvaluateurPerimetre implements PermissionEvaluator {

  private final Map<String, VerificateurPerimetre> parType;

  public EvaluateurPerimetre(List<VerificateurPerimetre> verificateurs) {
    this.parType =
        verificateurs.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    VerificateurPerimetre::typeCible, Function.identity()));
  }

  /** Refusé : les contrôles se font par identifiant, avant tout chargement d'objet. */
  @Override
  public boolean hasPermission(Authentication auth, Object cible, Object permission) {
    return false;
  }

  @Override
  public boolean hasPermission(
      Authentication auth, Serializable idCible, String typeCible, Object permission) {
    if (auth == null || idCible == null || typeCible == null || permission == null) {
      return false;
    }
    VerificateurPerimetre verificateur = parType.get(typeCible);
    return verificateur != null && verificateur.autorise(auth, idCible, permission.toString());
  }
}
