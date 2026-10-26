package sn.ecole.shared.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Résolution de l'école demandée.
 *
 * @param resolution {@code SOUS_DOMAINE} en production ({@code lycee-x.monapp.sn}), {@code ENTETE}
 *     en développement ({@code X-Tenant-Id}). Dans les deux cas l'appartenance est vérifiée.
 * @param domaineRacine domaine sous lequel sont les sous-domaines des écoles.
 */
@ConfigurationProperties("ecole.tenant")
public record TenantProperties(Resolution resolution, String domaineRacine) {

  public TenantProperties {
    if (resolution == null) {
      resolution = Resolution.SOUS_DOMAINE;
    }
  }

  public enum Resolution {
    ENTETE,
    SOUS_DOMAINE
  }
}
