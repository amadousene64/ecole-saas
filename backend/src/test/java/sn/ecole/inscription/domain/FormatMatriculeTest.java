package sn.ecole.inscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sn.ecole.shared.domain.ErreurMetier;

class FormatMatriculeTest {

  private static void refuse(String format) {
    assertThatThrownBy(() -> FormatMatricule.lire(format))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("FORMAT_MATRICULE_INVALIDE"));
  }

  @Test
  void leFormatParDefautDonneAnneeEtNumero() {
    FormatMatricule f = FormatMatricule.lire(FormatMatricule.PAR_DEFAUT);
    assertThat(f.generer(2026, 1)).isEqualTo("202600001");
    assertThat(f.generer(2026, 12345)).isEqualTo("202612345");
    assertThat(f.cleSequence(2026)).isEqualTo("2026");
  }

  @Test
  void texteFixeAnneeCourteEtMinusculesNormalisees() {
    FormatMatricule f = FormatMatricule.lire(" lya-{aa}-{seq:4} ");
    assertThat(f.format()).isEqualTo("LYA-{AA}-{SEQ:4}");
    assertThat(f.generer(2027, 42)).isEqualTo("LYA-27-0042");
    assertThat(f.generer(2000, 7)).isEqualTo("LYA-00-0007");
  }

  @Test
  void sansAnneeLeNumeroNeRepartJamais() {
    FormatMatricule f = FormatMatricule.lire("E/{SEQ:6}");
    assertThat(f.cleSequence(2026)).isEqualTo("*").isEqualTo(f.cleSequence(2031));
    assertThat(f.generer(2026, 3)).isEqualTo("E/000003");
  }

  @Test
  void unNumeroTropGrandPourLeFormatEstRefuse() {
    FormatMatricule f = FormatMatricule.lire("{SEQ:3}");
    assertThat(f.generer(2026, 999)).isEqualTo("999");
    assertThatThrownBy(() -> f.generer(2026, 1000))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("SEQUENCE_MATRICULE_EPUISEE"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "ABC",
        "{SEQ:2}",
        "{SEQ:9}",
        "{SEQ:4}{SEQ:4}",
        "A B{SEQ:4}",
        "{ANNEE}{SEQ:4}",
        "ÉCOLE{SEQ:4}",
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ{SEQ:5}"
      })
  void lesFormatsInvalidesSontRefuses(String format) {
    refuse(format);
  }

  @Test
  void nullEstRefuse() {
    refuse(null);
  }

  @Test
  void leMatriculeSaisiEstNormaliseEtControle() {
    assertThat(Matricule.verifier(" ab-12/3 ")).isEqualTo("AB-12/3");
    assertThatThrownBy(() -> Matricule.verifier("A B"))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("MATRICULE_INVALIDE"));
    assertThatThrownBy(() -> Matricule.verifier("-12")).isInstanceOf(ErreurMetier.class);
    assertThatThrownBy(() -> Matricule.verifier(null)).isInstanceOf(ErreurMetier.class);
  }
}
