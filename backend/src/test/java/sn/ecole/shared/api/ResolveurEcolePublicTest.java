package sn.ecole.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.shared.api.TenantProperties.Resolution;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

/** Quelle école une requête publique désigne-t-elle ? Jamais plus que ce que l'hôte permet. */
class ResolveurEcolePublicTest {

  private static final UUID LYCEE_X = UUID.fromString("01930000-0000-7000-8000-0000000000a1");
  private static final UUID FERMEE = UUID.fromString("01930000-0000-7000-8000-0000000000c3");
  private static final UUID PAR_DEFAUT = UUID.fromString("01930000-0000-7000-8000-0000000000b2");

  private static Tenant tenant(UUID id, boolean actif) {
    Tenant t = mock(Tenant.class);
    when(t.getId()).thenReturn(id);
    when(t.isActif()).thenReturn(actif);
    return t;
  }

  /** « lycee-x » (active), « ecole-fermee » (inactive) et l'école par défaut du poste. */
  private static final TenantRepository TENANTS =
      new TenantRepository() {
        @Override
        public Optional<Tenant> findById(UUID id) {
          if (id.equals(LYCEE_X) || id.equals(PAR_DEFAUT)) {
            return Optional.of(tenant(id, true));
          }
          return id.equals(FERMEE) ? Optional.of(tenant(id, false)) : Optional.empty();
        }

        @Override
        public Optional<Tenant> findBySousDomaine(String sousDomaine) {
          return switch (sousDomaine) {
            case "lycee-x" -> Optional.of(tenant(LYCEE_X, true));
            case "ecole-fermee" -> Optional.of(tenant(FERMEE, false));
            default -> Optional.empty();
          };
        }

        @Override
        public List<Tenant> findByActifTrueOrderByCode() {
          return List.of();
        }
      };

  private static ResolveurEcolePublic production() {
    return new ResolveurEcolePublic(
        new TenantProperties(Resolution.SOUS_DOMAINE, "monapp.sn", List.of("parent.monapp.sn")),
        TENANTS,
        "");
  }

  private static ResolveurEcolePublic developpement(String parDefaut) {
    return new ResolveurEcolePublic(
        new TenantProperties(Resolution.ENTETE, null, List.of()), TENANTS, parDefaut);
  }

  @Test
  void leSousDomaineDesigneLEcoleAvecOuSansPortEtSansCasse() {
    assertThat(production().resoudre("lycee-x.monapp.sn", null)).contains(LYCEE_X);
    assertThat(production().resoudre("Lycee-X.MonApp.sn:8443", null)).contains(LYCEE_X);
  }

  @Test
  void toutLeResteDonneLaMemeAbsenceDEcole() {
    ResolveurEcolePublic r = production();
    assertThat(r.resoudre("inconnue.monapp.sn", null)).isEmpty(); // sous-domaine inconnu
    assertThat(r.resoudre("ecole-fermee.monapp.sn", null)).isEmpty(); // école inactive
    assertThat(r.resoudre("parent.monapp.sn", null)).isEmpty(); // hôte commun
    assertThat(r.resoudre("lycee-x.autre.sn", null)).isEmpty(); // autre domaine
    assertThat(r.resoudre("monapp.sn", null)).isEmpty(); // le domaine racine lui-même
    assertThat(r.resoudre("a.b.monapp.sn", null)).isEmpty(); // sous-sous-domaine
    assertThat(r.resoudre("x_y.monapp.sn", null)).isEmpty(); // libellé invalide
    assertThat(r.resoudre("", null)).isEmpty();
    assertThat(r.resoudre(null, null)).isEmpty();
  }

  @Test
  void enProductionUnIdentifiantDEcoleEstIgnore() {
    // Seul le développement désigne une école par son identifiant : jamais sur un hôte inconnu.
    assertThat(production().resoudre("inconnue.monapp.sn", LYCEE_X)).isEmpty();
    assertThat(production().resoudre(null, LYCEE_X)).isEmpty();
  }

  @Test
  void enDeveloppementLIdentifiantOuLEcoleParDefautSontAcceptesSiActifs() {
    assertThat(developpement("").resoudre("localhost:4200", LYCEE_X)).contains(LYCEE_X);
    assertThat(developpement("").resoudre("localhost:4200", null)).isEmpty();
    assertThat(developpement(PAR_DEFAUT.toString()).resoudre("localhost", null))
        .contains(PAR_DEFAUT);
    // Une école inactive ou inconnue n'est jamais montrée.
    assertThat(developpement("").resoudre(null, FERMEE)).isEmpty();
    assertThat(developpement("").resoudre(null, UUID.randomUUID())).isEmpty();
  }
}
