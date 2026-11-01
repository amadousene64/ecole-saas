package sn.ecole.inscription.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import sn.ecole.inscription.domain.AntiVirus;

/** Interprétation des réponses de clamd ; le vrai clamd est couvert par AntivirusClamAvTest. */
class ClamAvClientTest {

  @Test
  void lesReponsesDeClamdSontInterpretees() {
    assertThat(ClamAvClient.interpreter("stream: OK\n"))
        .isEqualTo(new AntiVirus.Resultat(true, null));
    assertThat(ClamAvClient.interpreter("stream: Eicar-Test-Signature FOUND"))
        .isEqualTo(new AntiVirus.Resultat(false, "Eicar-Test-Signature"));
    assertThatThrownBy(() -> ClamAvClient.interpreter("INSTREAM size limit exceeded. ERROR"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> ClamAvClient.interpreter(""))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void clamdInjoignableEstUneAnalyseImpossible() throws Exception {
    int port;
    try (ServerSocket libre = new ServerSocket(0)) {
      port = libre.getLocalPort();
    }
    ClamAvClient client = new ClamAvClient("localhost", port, Duration.ofSeconds(2));
    assertThatThrownBy(() -> client.analyser(new byte[] {1, 2, 3}))
        .isInstanceOf(IllegalStateException.class);
  }
}
