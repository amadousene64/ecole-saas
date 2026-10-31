package sn.ecole.inscription.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.ecole.inscription.domain.AntiVirus;

/**
 * Client clamd, commande {@code zINSTREAM} : le contenu est envoyé en morceaux préfixés par leur
 * longueur (4 octets, gros-boutiste), terminés par une longueur nulle. Réponses : {@code stream:
 * OK} (sain) ou {@code stream: <signature> FOUND} (infecté) ; toute autre réponse (erreur, limite
 * de taille) est une analyse impossible, à retenter.
 */
@Component
class ClamAvClient implements AntiVirus {

  private static final int MORCEAU = 64 * 1024;
  private static final int REPONSE_MAX = 4096;

  private final String hote;
  private final int port;
  private final Duration delai;

  ClamAvClient(
      @Value("${ecole.clamav.hote:localhost}") String hote,
      @Value("${ecole.clamav.port:3310}") int port,
      @Value("${ecole.clamav.delai:60s}") Duration delai) {
    this.hote = hote;
    this.port = port;
    this.delai = delai;
  }

  @Override
  public Resultat analyser(byte[] contenu) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(hote, port), (int) Math.min(delai.toMillis(), 10_000));
      socket.setSoTimeout((int) delai.toMillis());
      DataOutputStream sortie = new DataOutputStream(socket.getOutputStream());
      sortie.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
      for (int debut = 0; debut < contenu.length; debut += MORCEAU) {
        int longueur = Math.min(MORCEAU, contenu.length - debut);
        sortie.writeInt(longueur);
        sortie.write(contenu, debut, longueur);
      }
      sortie.writeInt(0);
      sortie.flush();
      return interpreter(lireReponse(socket.getInputStream()));
    } catch (IOException e) {
      throw new IllegalStateException("Analyse antivirus impossible (clamd injoignable).", e);
    }
  }

  static Resultat interpreter(String reponse) {
    String r = reponse.strip();
    if (r.equals("stream: OK")) {
      return new Resultat(true, null);
    }
    if (r.startsWith("stream: ") && r.endsWith(" FOUND")) {
      return new Resultat(false, r.substring("stream: ".length(), r.length() - " FOUND".length()));
    }
    // Par exemple « INSTREAM size limit exceeded. ERROR » : ni sain ni infecté.
    throw new IllegalStateException("Réponse inattendue de clamd.");
  }

  private static String lireReponse(InputStream entree) throws IOException {
    ByteArrayOutputStream tampon = new ByteArrayOutputStream();
    int octet;
    while ((octet = entree.read()) > 0 && tampon.size() < REPONSE_MAX) {
      tampon.write(octet);
    }
    return tampon.toString(StandardCharsets.US_ASCII);
  }
}
