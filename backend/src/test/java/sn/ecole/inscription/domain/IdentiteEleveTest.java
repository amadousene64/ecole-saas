package sn.ecole.inscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.shared.domain.ErreurMetier;

class IdentiteEleveTest {

  private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 27);

  private static IdentiteEleve identite(
      String prenoms, String nom, LocalDate naissance, String pays, String identifiant) {
    return IdentiteEleve.valider(
        prenoms, nom, Sexe.F, naissance, "Ville Fictive", pays, identifiant, AUJOURDHUI);
  }

  private static void code(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void lIdentiteEstNormalisee() {
    IdentiteEleve i =
        identite("  Awa   Marie ", "essai-test", LocalDate.of(2012, 3, 4), "sn", " ab 12 ");
    assertThat(i.prenoms()).isEqualTo("Awa Marie");
    assertThat(i.nom()).isEqualTo("ESSAI-TEST");
    assertThat(i.nationalite()).isEqualTo("SN");
    assertThat(i.identifiantNational()).isEqualTo("AB12");
  }

  @Test
  void lesNomsAccentuesEtApostrophesSontAcceptes() {
    assertThat(identite("Élodie", "D'Essai", LocalDate.of(2015, 1, 1), "SN", null).nom())
        .isEqualTo("D'ESSAI");
  }

  @Test
  void lesChiffresDansUnNomSontRefuses() {
    code(() -> identite("Awa2", "Fictif", LocalDate.of(2012, 1, 1), "SN", null), "CHAMP_INVALIDE");
    code(() -> identite("Awa", " ", LocalDate.of(2012, 1, 1), "SN", null), "CHAMP_INVALIDE");
  }

  @Test
  void laDateDeNaissanceDoitEtreVraisemblable() {
    code(
        () -> identite("Awa", "Fictif", AUJOURDHUI.plusDays(1), "SN", null),
        "DATE_NAISSANCE_INVALIDE");
    code(
        () -> identite("Awa", "Fictif", AUJOURDHUI.minusYears(1), "SN", null),
        "DATE_NAISSANCE_INVALIDE");
    code(
        () -> identite("Awa", "Fictif", AUJOURDHUI.minusYears(31), "SN", null),
        "DATE_NAISSANCE_INVALIDE");
    code(() -> identite("Awa", "Fictif", null, "SN", null), "DATE_NAISSANCE_INVALIDE");
    assertThat(identite("Awa", "Fictif", AUJOURDHUI.minusYears(2), "SN", null)).isNotNull();
  }

  @Test
  void laNationaliteEstUnCodePays() {
    code(() -> identite("Awa", "Fictif", LocalDate.of(2012, 1, 1), "S1", null), "CHAMP_INVALIDE");
    code(() -> identite("Awa", "Fictif", LocalDate.of(2012, 1, 1), "SEN", null), "CHAMP_INVALIDE");
  }

  @Test
  void leSexeEstObligatoire() {
    code(
        () ->
            IdentiteEleve.valider(
                "Awa", "Fictif", null, LocalDate.of(2012, 1, 1), "V", "SN", null, AUJOURDHUI),
        "CHAMP_INVALIDE");
  }

  @Test
  void aucuneDonneeDansLesTraces() {
    IdentiteEleve i = identite("Awa", "Fictif", LocalDate.of(2012, 1, 1), "SN", null);
    assertThat(i.toString()).doesNotContain("Awa").doesNotContain("FICTIF").doesNotContain("2012");
    Eleve e = Eleve.creer("202600001", i);
    assertThat(e.toString()).doesNotContain("Awa").doesNotContain("FICTIF").contains("202600001");
  }

  @Test
  void laCleDeRechercheIgnoreAccentsEtCasse() {
    Eleve e =
        Eleve.creer("1", identite("Élodie  Ndèye", "Fâll", LocalDate.of(2012, 1, 1), "SN", null));
    assertThat(e.getNomRecherche()).isEqualTo("fall elodie ndeye");
    assertThat(Saisies.normaliser("  D’ESSAI   Àwa ")).isEqualTo("d'essai awa");
  }

  @Test
  void unSeulCompteParEleve() {
    Eleve e = Eleve.creer("1", identite("Awa", "Fictif", LocalDate.of(2012, 1, 1), "SN", null));
    UUID compte = UUID.randomUUID();
    e.relierCompte(compte);
    e.relierCompte(compte);
    code(() -> e.relierCompte(UUID.randomUUID()), "COMPTE_DEJA_RELIE");
  }

  @Test
  void laPhotoRemplaceeRenvoieLAncienneCle() {
    Eleve e = Eleve.creer("1", identite("Awa", "Fictif", LocalDate.of(2012, 1, 1), "SN", null));
    assertThat(e.changerPhoto("a", "image/png")).isNull();
    assertThat(e.changerPhoto("b", "image/png")).isEqualTo("a");
    assertThat(e.retirerPhoto()).isEqualTo("b");
    assertThat(e.getPhotoCle()).isNull();
  }

  @Test
  void lesParametresParDefautEtLeurModification() {
    ParametresInscription p = ParametresInscription.parDefaut();
    assertThat(p.getPolitiqueCapacite()).isEqualTo(PolitiqueCapacite.AVERTIR);
    assertThat(p.format().generer(2026, 1)).isEqualTo("202600001");
    p.modifier("x{seq:3}", PolitiqueCapacite.BLOQUER);
    assertThat(p.getFormatMatricule()).isEqualTo("X{SEQ:3}");
    code(() -> p.modifier("x", PolitiqueCapacite.BLOQUER), "FORMAT_MATRICULE_INVALIDE");
  }

  @Test
  void leDossierSensibleNeManipuleQueDesChiffres() {
    DossierSensible d = DossierSensible.pour(UUID.randomUUID());
    assertThat(d.renseigne(DossierSensible.Champ.SANTE)).isFalse();
    d.remplacer(DossierSensible.Champ.SANTE, new byte[] {1});
    d.remplacer(DossierSensible.Champ.ALLERGIES, new byte[] {2});
    d.remplacer(DossierSensible.Champ.SITUATION_FAMILIALE, new byte[] {3});
    assertThat(d.chiffre(DossierSensible.Champ.SITUATION_FAMILIALE)).containsExactly(3);
    assertThat(d.renseigne(DossierSensible.Champ.ALLERGIES)).isTrue();
    assertThat(d.toString()).startsWith("DossierSensible[");
  }
}
