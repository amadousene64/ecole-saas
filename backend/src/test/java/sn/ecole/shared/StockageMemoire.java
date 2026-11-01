package sn.ecole.shared;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import sn.ecole.shared.domain.StockageFichiers;

/** Stockage en mémoire pour les tests d'API : le vrai MinIO est couvert par StockageS3Test. */
public class StockageMemoire implements StockageFichiers {

  public record Objet(byte[] contenu, String contentType) {}

  private final Map<String, Objet> objets = new ConcurrentHashMap<>();

  @Override
  public void deposer(String cle, byte[] contenu, String contentType) {
    objets.put(cle, new Objet(contenu, contentType));
  }

  @Override
  public void supprimer(String cle) {
    objets.remove(cle);
  }

  @Override
  public byte[] lire(String cle) {
    Objet objet = objets.get(cle);
    if (objet == null) {
      throw new IllegalStateException("Objet absent du stockage.");
    }
    return objet.contenu();
  }

  @Override
  public URI urlSignee(String cle, Duration validite) {
    return URI.create("https://stockage.test/" + cle + "?expire=" + validite.toSeconds());
  }

  @Override
  public URI urlTelechargement(
      String cle, Duration validite, String contentType, String nomFichier) {
    return URI.create(
        "https://stockage.test/"
            + cle
            + "?expire="
            + validite.toSeconds()
            + "&disposition=attachment");
  }

  public Map<String, Objet> objets() {
    return objets;
  }

  public void vider() {
    objets.clear();
  }
}
