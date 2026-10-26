package sn.ecole.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

class EvaluateurPerimetreTest {

  private final Authentication utilisateur = new TestingAuthenticationToken("u", "x");

  private final EvaluateurPerimetre evaluateur =
      new EvaluateurPerimetre(
          List.of(
              new VerificateurPerimetre() {
                @Override
                public String typeCible() {
                  return "Classe";
                }

                @Override
                public boolean autorise(Authentication u, Serializable id, String permission) {
                  return "6A".equals(id) && "lire".equals(permission);
                }
              }));

  @Test
  void delegueAuVerificateurDuType() {
    assertThat(evaluateur.hasPermission(utilisateur, "6A", "Classe", "lire")).isTrue();
    assertThat(evaluateur.hasPermission(utilisateur, "6B", "Classe", "lire")).isFalse();
    assertThat(evaluateur.hasPermission(utilisateur, "6A", "Classe", "modifier")).isFalse();
  }

  @Test
  void refuseParDefaut() {
    assertThat(evaluateur.hasPermission(utilisateur, "6A", "TypeInconnu", "lire")).isFalse();
    assertThat(evaluateur.hasPermission(null, "6A", "Classe", "lire")).isFalse();
    assertThat(evaluateur.hasPermission(utilisateur, null, "Classe", "lire")).isFalse();
    assertThat(evaluateur.hasPermission(utilisateur, "6A", "Classe", null)).isFalse();
    assertThat(evaluateur.hasPermission(utilisateur, new Object(), "lire")).isFalse();
  }
}
