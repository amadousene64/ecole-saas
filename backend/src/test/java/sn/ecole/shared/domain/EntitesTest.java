package sn.ecole.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntitesTest {

  /** Entité d'essai pour la suppression logique (méthode protégée). */
  static final class EntiteEssai extends TenantAwareEntity {
    void supprimer(Instant quand) {
      marquerSupprime(quand);
    }
  }

  @Test
  void lesIdentifiantsSontDesUuidV7Croissants() {
    UUID premier = Identifiants.uuidV7();
    UUID second = Identifiants.uuidV7();
    assertThat(premier.version()).isEqualTo(7);
    assertThat(second.compareTo(premier)).isPositive();
  }

  @Test
  void uneEntiteNeuveADejaSonIdEtPasDeVersion() {
    EntiteEssai entite = new EntiteEssai();
    assertThat(entite.getId()).isNotNull();
    assertThat(entite.getVersion()).isNull();
    assertThat(entite.getTenantId()).isNull();
    assertThat(entite.getCreeLe()).isNull();
    assertThat(entite.getCreePar()).isNull();
    assertThat(entite.getModifieLe()).isNull();
    assertThat(entite.getModifiePar()).isNull();
  }

  @Test
  void legaliteSeFaitParIdentifiant() {
    EntiteEssai a = new EntiteEssai();
    EntiteEssai b = new EntiteEssai();
    assertThat(a).isEqualTo(a).isNotEqualTo(b).isNotEqualTo("autre chose");
    assertThat(a.hashCode()).isEqualTo(a.getId().hashCode());
  }

  @Test
  void laSuppressionEstLogique() {
    EntiteEssai entite = new EntiteEssai();
    assertThat(entite.estSupprime()).isFalse();
    Instant maintenant = Instant.now();
    entite.supprimer(maintenant);
    assertThat(entite.estSupprime()).isTrue();
    assertThat(entite.getSupprimeLe()).isEqualTo(maintenant);
  }

  @Test
  void sansContexteExigerLEcoleEchoueAvecUnCodeMetier() {
    assertThat(ContexteTenant.courant()).isEmpty();
    assertThatThrownBy(ContexteTenant::exiger)
        .isInstanceOfSatisfying(
            ErreurMetier.class,
            e -> {
              assertThat(e.code()).isEqualTo("TENANT_ABSENT");
              assertThat(e.type()).isEqualTo(TypeErreur.INVALIDE);
            });
  }

  @Test
  void leContexteNeVautQuePendantLeBloc() {
    UUID ecole = UUID.randomUUID();
    UUID vu = ContexteTenant.executer(ecole, ContexteTenant::exiger);
    assertThat(vu).isEqualTo(ecole);
    assertThat(ContexteTenant.courant()).isEmpty();
    assertThatThrownBy(() -> ContexteTenant.executer(null, () -> 1))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void uneAppartenanceExigeUtilisateurEtRole() {
    UUID utilisateur = UUID.randomUUID();
    Appartenance appartenance = Appartenance.attribuer(utilisateur, RoleEcole.PARENT);
    assertThat(appartenance.getUtilisateurId()).isEqualTo(utilisateur);
    assertThat(appartenance.getRole()).isEqualTo(RoleEcole.PARENT);
    assertThat(RoleEcole.PARENT.autorite()).isEqualTo("ROLE_PARENT");
    assertThatThrownBy(() -> Appartenance.attribuer(null, RoleEcole.PARENT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Appartenance.attribuer(utilisateur, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void unEvenementOutboxCompteSesEchecsEtSonTraitement() {
    UUID agregat = UUID.randomUUID();
    OutboxEvent evenement = new OutboxEvent("Billet", agregat, "BilletEmis", "{}");
    assertThat(evenement.getTypeAgregat()).isEqualTo("Billet");
    assertThat(evenement.getIdAgregat()).isEqualTo(agregat);
    assertThat(evenement.getTypeEvenement()).isEqualTo("BilletEmis");
    assertThat(evenement.getCharge()).isEqualTo("{}");
    assertThat(evenement.getTraiteLe()).isNull();

    evenement.enregistrerEchec("panne");
    evenement.enregistrerEchec("panne encore");
    Instant quand = Instant.now();
    evenement.marquerTraite(quand);

    assertThat(evenement.getTentatives()).isEqualTo(2);
    assertThat(evenement.getDerniereErreur()).isEqualTo("panne encore");
    assertThat(evenement.getTraiteLe()).isEqualTo(quand);
  }
}
