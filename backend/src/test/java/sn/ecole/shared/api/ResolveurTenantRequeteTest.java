package sn.ecole.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

class ResolveurTenantRequeteTest {

  private static final UUID LYCEE_X = UUID.randomUUID();

  /** Seul « lycee-x » existe. */
  private static final TenantRepository TENANTS =
      new TenantRepository() {
        @Override
        public Optional<Tenant> findById(UUID id) {
          return Optional.empty();
        }

        @Override
        public Optional<Tenant> findBySousDomaine(String sousDomaine) {
          if (!sousDomaine.equals("lycee-x")) {
            return Optional.empty();
          }
          Tenant tenant = org.mockito.Mockito.mock(Tenant.class);
          org.mockito.Mockito.when(tenant.getId()).thenReturn(LYCEE_X);
          return Optional.of(tenant);
        }

        @Override
        public List<Tenant> findByActifTrueOrderByCode() {
          return List.of();
        }
      };

  private static ResolveurTenantRequete enMode(TenantProperties.Resolution mode) {
    return new ResolveurTenantRequete(new TenantProperties(mode, "monapp.sn"), TENANTS);
  }

  private static MockHttpServletRequest requete(String hote, String entete) {
    MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/api/v1/moi");
    requete.setServerName(hote);
    if (entete != null) {
      requete.addHeader("X-Tenant-Id", entete);
    }
    return requete;
  }

  @Test
  void modeEnteteLitLUuid() {
    UUID ecole = UUID.randomUUID();
    assertThat(
            enMode(TenantProperties.Resolution.ENTETE)
                .resoudre(requete("localhost", ecole.toString())))
        .contains(ecole);
    assertThat(enMode(TenantProperties.Resolution.ENTETE).resoudre(requete("localhost", null)))
        .isEmpty();
  }

  @Test
  void modeEnteteRefuseUnUuidMalForme() {
    assertThatThrownBy(
            () -> enMode(TenantProperties.Resolution.ENTETE).resoudre(requete("localhost", "abc")))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("TENANT_INVALIDE"));
  }

  @Test
  void modeSousDomaineTrouveLEcole() {
    var resolveur = enMode(TenantProperties.Resolution.SOUS_DOMAINE);
    assertThat(resolveur.resoudre(requete("lycee-x.monapp.sn", null))).contains(LYCEE_X);
    assertThat(resolveur.resoudre(requete("LYCEE-X.MonApp.sn", null))).contains(LYCEE_X);
  }

  @Test
  void modeSousDomaineIgnoreLesHotesHorsDomaine() {
    var resolveur = enMode(TenantProperties.Resolution.SOUS_DOMAINE);
    assertThat(resolveur.resoudre(requete("lycee-x.autre.sn", null))).isEmpty();
    assertThat(resolveur.resoudre(requete("monapp.sn", null))).isEmpty();
    assertThat(resolveur.resoudre(requete("a.b.monapp.sn", null))).isEmpty();
  }

  @Test
  void modeSousDomaineInconnuDonneUnIdentifiantSansAppartenance() {
    assertThat(
            enMode(TenantProperties.Resolution.SOUS_DOMAINE)
                .resoudre(requete("inconnu.monapp.sn", null)))
        .contains(ContexteTenant.AUCUN);
  }

  @Test
  void modeSousDomaineRefuseUnEnteteContradictoire() {
    var resolveur = enMode(TenantProperties.Resolution.SOUS_DOMAINE);
    assertThatThrownBy(
            () -> resolveur.resoudre(requete("lycee-x.monapp.sn", UUID.randomUUID().toString())))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("TENANT_INCOHERENT"));
    assertThat(resolveur.resoudre(requete("lycee-x.monapp.sn", LYCEE_X.toString())))
        .contains(LYCEE_X);
  }

  @Test
  void modeSousDomaineExigeLeDomaineRacine() {
    assertThatThrownBy(
            () ->
                new ResolveurTenantRequete(
                    new TenantProperties(TenantProperties.Resolution.SOUS_DOMAINE, " "), TENANTS))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void leModeParDefautEstLeSousDomaine() {
    assertThat(new TenantProperties(null, "monapp.sn").resolution())
        .isEqualTo(TenantProperties.Resolution.SOUS_DOMAINE);
  }

  private static ResolveurTenantRequete avecHotesCommuns(String... hotes) {
    return new ResolveurTenantRequete(
        new TenantProperties(TenantProperties.Resolution.SOUS_DOMAINE, "monapp.sn", List.of(hotes)),
        TENANTS);
  }

  @Test
  void surUnHoteCommunLEcoleEstCelleDeLEntete() {
    var resolveur = avecHotesCommuns("parent.monapp.sn");
    UUID ecole = UUID.randomUUID();
    assertThat(resolveur.resoudre(requete("parent.monapp.sn", ecole.toString()))).contains(ecole);
    assertThat(resolveur.resoudre(requete("PARENT.MonApp.sn", LYCEE_X.toString())))
        .contains(LYCEE_X);
    // Sans en-tête : aucune école désignée (jamais le sous-domaine « parent », ni AUCUN).
    assertThat(resolveur.resoudre(requete("parent.monapp.sn", null))).isEmpty();
  }

  @Test
  void surUnHoteCommunUnEnteteMalFormeResteRefuse() {
    assertThatThrownBy(
            () -> avecHotesCommuns("parent.monapp.sn").resoudre(requete("parent.monapp.sn", "abc")))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("TENANT_INVALIDE"));
  }

  @Test
  void surUnSousDomaineDEcoleLeModeResteStrictMemeAvecUnHoteCommunDeclare() {
    var resolveur = avecHotesCommuns("parent.monapp.sn");
    assertThatThrownBy(
            () -> resolveur.resoudre(requete("lycee-x.monapp.sn", UUID.randomUUID().toString())))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("TENANT_INCOHERENT"));
    assertThat(resolveur.resoudre(requete("lycee-x.monapp.sn", null))).contains(LYCEE_X);
    // Un hôte voisin non déclaré n'hérite de rien.
    assertThatThrownBy(
            () -> resolveur.resoudre(requete("parent2.monapp.sn", UUID.randomUUID().toString())))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("TENANT_INCOHERENT"));
  }

  @Test
  void unHoteAvecPointFinalNEstPasCommunNiUneEcole() {
    var resolveur = avecHotesCommuns("parent.monapp.sn");
    // « parent.monapp.sn. » n'est pas l'hôte déclaré : le mode strict s'applique, et rien n'est lu.
    assertThatThrownBy(
            () -> resolveur.resoudre(requete("parent.monapp.sn.", UUID.randomUUID().toString())))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("TENANT_INCOHERENT"));
    assertThat(resolveur.resoudre(requete("parent.monapp.sn.", null))).isEmpty();
  }

  @Test
  void sansHoteCommunDeclareRienNeChange() {
    assertThatThrownBy(
            () ->
                enMode(TenantProperties.Resolution.SOUS_DOMAINE)
                    .resoudre(requete("parent.monapp.sn", UUID.randomUUID().toString())))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("TENANT_INCOHERENT"));
  }

  @Test
  void leshotesCommunsSontNormalisesEtValides() {
    var proprietes =
        new TenantProperties(
            TenantProperties.Resolution.SOUS_DOMAINE,
            "monapp.sn",
            java.util.Arrays.asList(" Parent.MonApp.sn ", "", null, "parent.monapp.sn"));
    assertThat(proprietes.hotesCommuns()).containsExactly("parent.monapp.sn");
    assertThat(proprietes.estHoteCommun("PARENT.monapp.sn")).isTrue();
    assertThat(proprietes.estHoteCommun("autre.monapp.sn")).isFalse();
    assertThatThrownBy(
            () ->
                new TenantProperties(
                    TenantProperties.Resolution.SOUS_DOMAINE, "monapp.sn", List.of("*.monapp.sn")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TenantProperties(
                    TenantProperties.Resolution.SOUS_DOMAINE, "monapp.sn", List.of("localhost")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
