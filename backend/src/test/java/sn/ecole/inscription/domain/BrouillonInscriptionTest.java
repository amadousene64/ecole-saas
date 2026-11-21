package sn.ecole.inscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.shared.domain.ErreurMetier;

class BrouillonInscriptionTest {

  private static final byte[] CHIFFRE = {1, 2, 3};

  private static BrouillonInscription brouillon() {
    return BrouillonInscription.ouvrir(UUID.randomUUID(), UUID.randomUUID());
  }

  @Test
  void unBrouillonOuvertCommenceALEtape1SansContenu() {
    BrouillonInscription b = brouillon();
    assertThat(b.getEtape()).isEqualTo(1);
    assertThat(b.getContenu()).isNull();
  }

  @Test
  void leContenuEtLEtapeSontRemplacesEnBloc() {
    BrouillonInscription b = brouillon();
    b.remplacer(CHIFFRE, 3);
    assertThat(b.getEtape()).isEqualTo(3);
    assertThat(b.getContenu()).isEqualTo(CHIFFRE);
  }

  @Test
  void lEtapeVaDe1A5() {
    BrouillonInscription b = brouillon();
    for (int etape : new int[] {0, 6}) {
      assertThatThrownBy(() -> b.remplacer(CHIFFRE, etape))
          .isInstanceOfSatisfying(
              ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("ETAPE_INVALIDE"));
    }
  }

  @Test
  void unBrouillonClosNaPlusDeContenuEtNeSeModifiePlus() {
    BrouillonInscription b = brouillon();
    b.remplacer(CHIFFRE, 2);
    b.clore(Instant.now());
    assertThat(b.getContenu()).isNull();
    assertThat(b.estSupprime()).isTrue();
    assertThatThrownBy(() -> b.remplacer(CHIFFRE, 3))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.code()).isEqualTo("BROUILLON_CLOS"));
  }

  @Test
  void unePieceDuBrouillonPasseAuDossierDeLEleveUneSeuleFois() {
    UUID brouillon = UUID.randomUUID();
    UUID eleve = UUID.randomUUID();
    PieceDossier piece =
        PieceDossier.deposerPourBrouillon(
            brouillon,
            PieceDossier.Type.ACTE_NAISSANCE,
            "cle",
            new ControlePiece.PieceValidee(new byte[] {1}, "application/pdf", 1, "abc"));
    assertThat(piece.typeRattachement()).isEqualTo("BrouillonInscription");
    assertThat(piece.idRattachement()).isEqualTo(brouillon);

    piece.rattacherA(eleve);
    assertThat(piece.getEleveId()).isEqualTo(eleve);
    assertThat(piece.getBrouillonId()).isNull();
    assertThat(piece.typeRattachement()).isEqualTo("Eleve");
    assertThatThrownBy(() -> piece.rattacherA(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }
}
