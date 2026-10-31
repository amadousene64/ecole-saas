package sn.ecole.inscription.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Contrôles de saisie du module. Les messages ne citent jamais la valeur saisie (nom, téléphone) :
 * une erreur peut être journalisée ou affichée à un autre utilisateur.
 */
public final class Saisies {

  /** Lettres (accents compris), espaces, trait d'union, apostrophe, point. */
  private static final Pattern NOM = Pattern.compile("[\\p{L}][\\p{L}\\p{M} '’.-]*");

  private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");

  private Saisies() {}

  public static ErreurMetier invalide(String code, String message) {
    return new ErreurMetier(code, TypeErreur.INVALIDE, message);
  }

  public static ErreurMetier regle(String code, String message) {
    return new ErreurMetier(code, TypeErreur.REGLE_METIER, message);
  }

  public static ErreurMetier conflit(String code, String message) {
    return new ErreurMetier(code, TypeErreur.CONFLIT, message);
  }

  /** Texte obligatoire : rogné, espaces multiples réduits, non vide, au plus {@code max}. */
  public static String exiger(String valeur, String champ, int max) {
    String propre = valeur == null ? "" : valeur.strip().replaceAll("\\s+", " ");
    if (propre.isEmpty() || propre.length() > max) {
      throw invalide(
          "CHAMP_INVALIDE", champ + " est obligatoire (" + max + " caractères au plus).");
    }
    return propre;
  }

  /** Texte facultatif : vide devient {@code null}. */
  public static String optionnel(String valeur, String champ, int max) {
    String propre = valeur == null ? "" : valeur.strip().replaceAll("\\s+", " ");
    if (propre.isEmpty()) {
      return null;
    }
    if (propre.length() > max) {
      throw invalide("CHAMP_INVALIDE", champ + " dépasse " + max + " caractères.");
    }
    return propre;
  }

  /** Nom ou prénoms d'une personne. */
  public static String nomPersonne(String valeur, String champ, int max) {
    String propre = exiger(valeur, champ, max);
    if (!NOM.matcher(propre).matches()) {
      throw invalide("CHAMP_INVALIDE", champ + " ne contient que des lettres.");
    }
    return propre;
  }

  /** Minuscules sans accents ni espaces superflus : clé de recherche et de tri. */
  public static String normaliser(String texte) {
    String sansAccents =
        DIACRITIQUES.matcher(Normalizer.normalize(texte, Normalizer.Form.NFD)).replaceAll("");
    return sansAccents.toLowerCase(Locale.ROOT).replace('’', '\'').strip().replaceAll("\\s+", " ");
  }
}
