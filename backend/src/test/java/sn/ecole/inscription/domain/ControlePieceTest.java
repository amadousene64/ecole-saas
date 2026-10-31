package sn.ecole.inscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.shared.domain.ErreurMetier;

class ControlePieceTest {

  private static void code(byte[] contenu, String code) {
    assertThatThrownBy(() -> ControlePiece.controler(contenu))
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void leTypeVientDesOctetsMagiques() {
    assertThat(
            ControlePiece.controler("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII))
                .contentType())
        .isEqualTo("application/pdf");
    assertThat(
            ControlePiece.controler(
                    new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0})
                .contentType())
        .isEqualTo("image/png");
    assertThat(
            ControlePiece.controler(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})
                .contentType())
        .isEqualTo("image/jpeg");
  }

  @Test
  void lesAutresFormatsSontRefusesQuelQueSoitLeNom() {
    code("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1), "FICHIER_INVALIDE"); // exécutable
    code("<html><script>".getBytes(StandardCharsets.US_ASCII), "FICHIER_INVALIDE");
    code("PK\u0003\u0004".getBytes(StandardCharsets.ISO_8859_1), "FICHIER_INVALIDE"); // zip, docx
    code("%PD".getBytes(StandardCharsets.US_ASCII), "FICHIER_INVALIDE");
    code(new byte[0], "FICHIER_INVALIDE");
    code(null, "FICHIER_INVALIDE");
  }

  @Test
  void laTailleEstBornee() {
    byte[] gros = new byte[ControlePiece.TAILLE_MAX + 1];
    System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, gros, 0, 5);
    code(gros, "FICHIER_TROP_VOLUMINEUX");
  }

  @Test
  void lEmpreinteEstLeSha256DuContenu() throws Exception {
    byte[] contenu = "%PDF-1.4 contenu fictif".getBytes(StandardCharsets.US_ASCII);
    String attendu =
        java.util.HexFormat.of()
            .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(contenu));
    assertThat(ControlePiece.controler(contenu).sha256()).isEqualTo(attendu).hasSize(64);
  }

  @Test
  void lAnalyseNeSEnregistreQuUneFoisEtSeuleUnePieceSaineEstDisponible() {
    PieceDossier p =
        PieceDossier.deposer(
            UUID.randomUUID(),
            PieceDossier.Type.ACTE_NAISSANCE,
            "cle",
            ControlePiece.controler("%PDF-1.4".getBytes(StandardCharsets.US_ASCII)));
    assertThat(p.disponible()).isFalse();
    assertThat(p.nomFichier()).isEqualTo("acte_naissance.pdf");
    assertThat(p.enregistrerAnalyse(false, Instant.now())).isTrue();
    assertThat(p.getStatut()).isEqualTo(PieceDossier.Statut.INFECTE);
    assertThat(p.enregistrerAnalyse(true, Instant.now())).isFalse();
    assertThat(p.disponible()).isFalse();
  }
}
