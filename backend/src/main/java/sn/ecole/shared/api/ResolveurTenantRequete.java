package sn.ecole.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Détermine l'école DEMANDÉE par la requête. Ce n'est qu'une demande : le filtre vérifie ensuite
 * que l'utilisateur en est membre.
 */
class ResolveurTenantRequete {

  static final String ENTETE = "X-Tenant-Id";
  private static final Pattern LIBELLE_DNS =
      Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

  private final TenantProperties proprietes;
  private final TenantRepository tenants;

  ResolveurTenantRequete(TenantProperties proprietes, TenantRepository tenants) {
    this.proprietes = proprietes;
    this.tenants = tenants;
    if (proprietes.resolution() == TenantProperties.Resolution.SOUS_DOMAINE
        && (proprietes.domaineRacine() == null || proprietes.domaineRacine().isBlank())) {
      throw new IllegalStateException(
          "ecole.tenant.domaine-racine est obligatoire en mode SOUS_DOMAINE");
    }
  }

  /** Vide si la requête ne désigne aucune école. */
  Optional<UUID> resoudre(HttpServletRequest requete) {
    Optional<UUID> parEntete = lireEntete(requete);
    if (proprietes.resolution() == TenantProperties.Resolution.ENTETE) {
      return parEntete;
    }
    // Hôte commun déclaré (app parent) : l'école est désignée par l'en-tête, contrôlée ensuite par
    // les appartenances. Tout autre hôte reste strict.
    if (proprietes.estHoteCommun(requete.getServerName())) {
      return parEntete;
    }
    // Hôte commun déclaré (app parent) : l'école est désignée par l'en-tête, contrôlée ensuite par
    // les appartenances. Tout autre hôte reste strict.
    if (proprietes.estHoteCommun(requete.getServerName())) {
      return parEntete;
    }
    Optional<UUID> parSousDomaine = lireSousDomaine(requete);
    if (parEntete.isPresent() && !parEntete.equals(parSousDomaine)) {
      throw new ErreurMetier(
          "TENANT_INCOHERENT",
          TypeErreur.INVALIDE,
          "L'école de l'en-tête X-Tenant-Id ne correspond pas au sous-domaine.");
    }
    return parSousDomaine;
  }

  private static Optional<UUID> lireEntete(HttpServletRequest requete) {
    String valeur = requete.getHeader(ENTETE);
    if (valeur == null || valeur.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(valeur.strip()));
    } catch (IllegalArgumentException e) {
      throw new ErreurMetier(
          "TENANT_INVALIDE", TypeErreur.INVALIDE, "L'en-tête X-Tenant-Id n'est pas un UUID.");
    }
  }

  private Optional<UUID> lireSousDomaine(HttpServletRequest requete) {
    String hote = requete.getServerName().toLowerCase(Locale.ROOT);
    String suffixe = "." + proprietes.domaineRacine().toLowerCase(Locale.ROOT);
    if (!hote.endsWith(suffixe)) {
      return Optional.empty();
    }
    String sousDomaine = hote.substring(0, hote.length() - suffixe.length());
    if (!LIBELLE_DNS.matcher(sousDomaine).matches()) {
      return Optional.empty();
    }
    // Sous-domaine inconnu : on renvoie un identifiant qui ne correspond à aucune appartenance,
    // pour répondre exactement comme pour une école dont on n'est pas membre (pas d'énumération).
    return Optional.of(
        tenants.findBySousDomaine(sousDomaine).map(Tenant::getId).orElse(ContexteTenant.AUCUN));
  }
}
