package sn.ecole.inscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.inscription.domain.FormatCodeActivation.CodeTire;
import sn.ecole.shared.domain.ErreurMetier;

class ResponsablesEtCodesTest {

  private static final LienParental.Droits TOUS =
      new LienParental.Droits(true, true, true, true, true);

  private static void code(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void leResponsableEstNormalise() {
    Responsable r =
        Responsable.creer("Moussa", "essai", "77 000 00 01", " Parent@Exemple.TEST ", "");
    assertThat(r.getNom()).isEqualTo("ESSAI");
    assertThat(r.getTelephone()).isEqualTo("+221770000001");
    assertThat(r.getEmail()).isEqualTo("parent@exemple.test");
    assertThat(r.getProfession()).isNull();
    assertThat(r.toString()).doesNotContain("Moussa").doesNotContain("+221");
  }

  @Test
  void telephoneObligatoireEtEmailValide() {
    code(() -> Responsable.creer("A", "B", " ", null, null), "TELEPHONE_INVALIDE");
    code(() -> Responsable.creer("A", "B", "12", null, null), "TELEPHONE_INVALIDE");
    code(() -> Responsable.creer("A", "B", "770000001", "pas-un-mail", null), "EMAIL_INVALIDE");
  }

  @Test
  void unCompteParResponsableEtDeliaison() {
    Responsable r = Responsable.creer("A", "B", "770000001", null, null);
    UUID compte = UUID.randomUUID();
    r.relierCompte(compte);
    code(() -> r.relierCompte(UUID.randomUUID()), "COMPTE_DEJA_RELIE");
    assertThat(r.delierCompte()).isEqualTo(compte);
    assertThat(r.delierCompte()).isNull();
    r.relierCompte(UUID.randomUUID());
  }

  @Test
  void sansAccesEspaceParentAucuneNotification() {
    LienParental lien =
        LienParental.creer(UUID.randomUUID(), UUID.randomUUID(), TypeLien.MERE, TOUS);
    assertThat(lien.notifie()).isTrue();
    lien.definirAccesEspaceParent(false);
    assertThat(lien.notifie()).isFalse();
    assertThat(lien.isRecoitNotifications()).isFalse();
    code(() -> lien.modifier(TypeLien.MERE, TOUS), "NOTIFICATIONS_INTERDITES");
    lien.modifier(TypeLien.TUTEUR, new LienParental.Droits(true, false, false, false, false));
    lien.definirAccesEspaceParent(true);
    assertThat(lien.notifie()).isFalse(); // pas rétablies d'office
    lien.modifier(TypeLien.TUTEUR, TOUS);
    assertThat(lien.notifie()).isTrue();
    assertThat(lien.droits()).isEqualTo(TOUS);
  }

  @Test
  void lesCodesSontAleatoiresEtLisibles() {
    Set<String> vus = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      CodeTire c = FormatCodeActivation.tirer();
      assertThat(c.presentation())
          .matches("[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}");
      vus.add(c.presentation());
      assertThat(c.toString()).doesNotContain(c.secret());
    }
    assertThat(vus).hasSize(200);
  }

  @Test
  void laSaisieTolereCasseTiretsEtConfusions() {
    CodeTire c = FormatCodeActivation.lire(" ab1o-k2lm-9xyz ").orElseThrow();
    assertThat(c.identifiant()).isEqualTo("AB10");
    assertThat(c.secret()).isEqualTo("K21M9XYZ");
    assertThat(FormatCodeActivation.lire("AB10K21M9XY")).isEmpty();
    assertThat(FormatCodeActivation.lire("AB10-K21M-9XYU")).isEmpty(); // U hors alphabet
    assertThat(FormatCodeActivation.lire(null)).isEmpty();
    assertThat(FormatCodeActivation.lire("x".repeat(50))).isEmpty();
  }

  @Test
  void unCodeSEpuiseExpireEtNeSertQuUneFois() {
    Instant t0 = Instant.parse("2026-10-27T10:00:00Z");
    CodeActivation c =
        CodeActivation.emettre(
            CodeActivation.Cible.RESPONSABLE, UUID.randomUUID(), "AB10", new byte[] {1}, t0);
    assertThat(c.utilisable(t0.plus(CodeActivation.VALIDITE).minusSeconds(1))).isTrue();
    assertThat(c.utilisable(t0.plus(CodeActivation.VALIDITE))).isFalse();
    for (int i = 0; i < CodeActivation.TENTATIVES_MAX - 1; i++) {
      c.enregistrerEchec();
    }
    assertThat(c.getStatut()).isEqualTo(CodeActivation.Statut.ACTIF);
    c.enregistrerEchec();
    assertThat(c.getStatut()).isEqualTo(CodeActivation.Statut.EPUISE);
    assertThat(c.utilisable(t0)).isFalse();
    c.enregistrerEchec();
    assertThat(c.getTentatives()).isEqualTo(CodeActivation.TENTATIVES_MAX);

    CodeActivation d =
        CodeActivation.emettre(
            CodeActivation.Cible.ELEVE, UUID.randomUUID(), "CD20", new byte[] {2}, t0);
    UUID compte = UUID.randomUUID();
    d.utiliser(compte, t0);
    assertThat(d.getStatut()).isEqualTo(CodeActivation.Statut.UTILISE);
    assertThat(d.getUtilisePar()).isEqualTo(compte);
    assertThatThrownBy(() -> d.utiliser(compte, t0)).isInstanceOf(IllegalStateException.class);
    d.revoquer();
    assertThat(d.getStatut()).isEqualTo(CodeActivation.Statut.UTILISE);

    CodeActivation e =
        CodeActivation.emettre(
            CodeActivation.Cible.ELEVE, UUID.randomUUID(), "EF30", new byte[] {3}, t0);
    e.revoquer();
    assertThat(e.utilisable(t0)).isFalse();
    assertThat(e.getEmpreinte()).containsExactly(3);
  }
}
