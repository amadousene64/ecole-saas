package sn.ecole.shared.api;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Résolution de l'école demandée.
 *
 * @param resolution {@code SOUS_DOMAINE} en production ({@code lycee-x.monapp.sn}), {@code ENTETE}
 *     en développement ({@code X-Tenant-Id}). Dans les deux cas l'appartenance est vérifiée.
 * @param domaineRacine domaine sous lequel sont les sous-domaines des écoles.
 * @param hotesCommuns hôtes qui ne désignent aucune école (ex. {@code parent.monapp.sn}, l'app
 *     parent commune à toutes les écoles). Sur ces hôtes seulement, en mode {@code SOUS_DOMAINE},
 *     l'école est celle de l'en-tête {@code X-Tenant-Id} ; l'appartenance est contrôlée comme
 *     partout. Sur tout autre hôte, le sous-domaine reste strict. Variable {@code
 *     ECOLE_HOTES_COMMUNS}, séparée par des virgules.
 */
@ConfigurationProperties("ecole.tenant")
public record TenantProperties(
    Resolution resolution, String domaineRacine, List<String> hotesCommuns) {

  private static final Pattern HOTE =
      Pattern.compile(
          "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");

  @ConstructorBinding
  public TenantProperties {
    if (resolution == null) {
      resolution = Resolution.SOUS_DOMAINE;
    }
    hotesCommuns =
        hotesCommuns == null
            ? List.of()
            : hotesCommuns.stream()
                .filter(h -> h != null && !h.isBlank())
                .map(h -> h.strip().toLowerCase(Locale.ROOT))
                .peek(
                    h -> {
                      if (!HOTE.matcher(h).matches()) {
                        throw new IllegalArgumentException(
                            "ecole.tenant.hotes-communs : hôte invalide « " + h + " »");
                      }
                    })
                .distinct()
                .toList();
  }

  public TenantProperties(Resolution resolution, String domaineRacine) {
    this(resolution, domaineRacine, List.of());
  }

  /** Vrai si cet hôte est déclaré commun (comparaison sans casse, sans port). */
  public boolean estHoteCommun(String hote) {
    return hote != null && hotesCommuns.contains(hote.strip().toLowerCase(Locale.ROOT));
  }

  public enum Resolution {
    ENTETE,
    SOUS_DOMAINE
  }
}
