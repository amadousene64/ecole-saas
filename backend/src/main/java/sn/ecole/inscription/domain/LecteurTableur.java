package sn.ecole.inscription.domain;

import java.util.List;
import java.util.Map;

/**
 * Lecture d'un tableur importé (CSV ou XLSX, reconnus par leur contenu). La première ligne non vide
 * porte les en-têtes ; chaque ligne suivante devient une table en-tête → valeur (texte rogné, dates
 * Excel en AAAA-MM-JJ). Les lignes entièrement vides sont ignorées mais comptées dans la
 * numérotation, pour que le rapport cite les numéros de ligne du fichier.
 */
public interface LecteurTableur {

  /** Taille maximale du fichier (octets). */
  int TAILLE_MAX = 2_000_000;

  /** Nombre maximal de lignes de données. */
  int LIGNES_MAX = 2_000;

  /** Ligne lue : numéro dans le fichier (en-têtes = 1) et valeurs par en-tête d'origine. */
  record Ligne(int numero, Map<String, String> valeurs) {

    @Override
    public String toString() {
      return "Ligne[" + numero + "]";
    }
  }

  /**
   * @throws sn.ecole.shared.domain.ErreurMetier FICHIER_INVALIDE (format, encodage, taille) ou
   *     IMPORT_TROP_GRAND (plus de {@link #LIGNES_MAX} lignes)
   */
  List<Ligne> lire(byte[] contenu);
}
