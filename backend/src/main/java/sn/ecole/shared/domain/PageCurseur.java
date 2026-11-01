package sn.ecole.shared.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

/**
 * Page d'une liste paginée par curseur (pagination « keyset ») : {@code curseurSuivant} est opaque
 * pour le client et {@code null} sur la dernière page. Stable même si des lignes sont ajoutées
 * pendant le parcours, et sans {@code OFFSET} coûteux.
 */
public record PageCurseur<T>(List<T> elements, String curseurSuivant) {

  private static final String SEPARATEUR = "\u001F";
  public static final int TAILLE_MAX = 100;

  public PageCurseur {
    elements = List.copyOf(elements);
  }

  public <R> PageCurseur<R> map(Function<? super T, ? extends R> transformation) {
    return new PageCurseur<>(
        elements.stream().<R>map(transformation::apply).toList(), curseurSuivant);
  }

  /**
   * Construit la page à partir de {@code taille + 1} lignes lues : la ligne en trop signale qu'il
   * existe une page suivante, dont le curseur est la clé de la dernière ligne gardée.
   */
  public static <T> PageCurseur<T> depuis(
      List<T> lues, int taille, Function<? super T, String[]> cle) {
    if (lues.size() <= taille) {
      return new PageCurseur<>(lues, null);
    }
    List<T> page = lues.subList(0, taille);
    return new PageCurseur<>(page, encoder(cle.apply(page.get(taille - 1))));
  }

  /** Taille demandée bornée à [1, {@link #TAILLE_MAX}] ; 50 par défaut. */
  public static int taille(Integer demandee) {
    if (demandee == null) {
      return 50;
    }
    return Math.clamp(demandee, 1, TAILLE_MAX);
  }

  public static String encoder(String... parties) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(String.join(SEPARATEUR, parties).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @throws ErreurMetier CURSEUR_INVALIDE si le curseur n'a pas été produit par {@link #encoder}
   */
  public static String[] decoder(String curseur, int nombreParties) {
    try {
      String[] parties =
          new String(Base64.getUrlDecoder().decode(curseur), StandardCharsets.UTF_8)
              .split(SEPARATEUR, -1);
      if (parties.length != nombreParties) {
        throw new IllegalArgumentException();
      }
      return parties;
    } catch (IllegalArgumentException e) {
      throw new ErreurMetier("CURSEUR_INVALIDE", TypeErreur.INVALIDE, "Curseur de page invalide.");
    }
  }
}
