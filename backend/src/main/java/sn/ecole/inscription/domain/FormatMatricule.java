package sn.ecole.inscription.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format des matricules de l'école : texte fixe et jetons.
 *
 * <ul>
 *   <li>{@code {AAAA}} : année de création (ex. 2026) ; {@code {AA}} : ses deux derniers chiffres ;
 *   <li>{@code {SEQ:n}} : numéro d'ordre sur n chiffres (3 à 8), obligatoire et unique. Il repart à
 *       1 chaque année si le format contient l'année, sinon il ne repart jamais.
 * </ul>
 *
 * Exemples : {@code {AAAA}{SEQ:5}} donne 202600001 ; {@code LYA-{AA}-{SEQ:4}} donne LYA-26-0001.
 */
public final class FormatMatricule {

  public static final String PAR_DEFAUT = "{AAAA}{SEQ:5}";

  private static final Pattern JETON = Pattern.compile("\\{(AAAA|AA|SEQ:(\\d))}");
  private static final Pattern TEXTE = Pattern.compile("[A-Z0-9/-]*");

  private sealed interface Partie {}

  private record Texte(String valeur) implements Partie {}

  private record Annee(boolean complete) implements Partie {}

  private record Sequence(int chiffres) implements Partie {}

  private final String format;
  private final List<Partie> parties;
  private final boolean avecAnnee;
  private final int chiffres;

  private FormatMatricule(String format, List<Partie> parties) {
    this.format = format;
    this.parties = List.copyOf(parties);
    this.avecAnnee = parties.stream().anyMatch(Annee.class::isInstance);
    this.chiffres =
        parties.stream()
            .filter(Sequence.class::isInstance)
            .mapToInt(p -> ((Sequence) p).chiffres())
            .findFirst()
            .orElseThrow();
  }

  /**
   * @throws sn.ecole.shared.domain.ErreurMetier FORMAT_MATRICULE_INVALIDE
   */
  public static FormatMatricule lire(String format) {
    String propre = format == null ? "" : format.strip().toUpperCase(Locale.ROOT);
    if (propre.isEmpty() || propre.length() > 60) {
      throw invalide("Le format est obligatoire (60 caractères au plus).");
    }
    List<Partie> parties = new ArrayList<>();
    Matcher m = JETON.matcher(propre);
    int position = 0;
    int sequences = 0;
    while (m.find()) {
      ajouterTexte(parties, propre.substring(position, m.start()));
      if (m.group(1).equals("AAAA")) {
        parties.add(new Annee(true));
      } else if (m.group(1).equals("AA")) {
        parties.add(new Annee(false));
      } else {
        int n = Integer.parseInt(m.group(2));
        if (n < 3 || n > 8) {
          throw invalide("Le numéro d'ordre {SEQ:n} compte de 3 à 8 chiffres.");
        }
        parties.add(new Sequence(n));
        sequences++;
      }
      position = m.end();
    }
    ajouterTexte(parties, propre.substring(position));
    if (sequences != 1) {
      throw invalide("Le format contient exactement un numéro d'ordre {SEQ:n}.");
    }
    FormatMatricule resultat = new FormatMatricule(propre, parties);
    if (resultat.longueurMax() > 30) {
      throw invalide("Un matricule compte 30 caractères au plus.");
    }
    return resultat;
  }

  private static void ajouterTexte(List<Partie> parties, String texte) {
    if (texte.isEmpty()) {
      return;
    }
    if (!TEXTE.matcher(texte).matches()) {
      throw invalide(
          "Hors jetons, le format ne contient que des lettres, chiffres, « - » et « / ».");
    }
    parties.add(new Texte(texte));
  }

  /** Clé du compteur : l'année si le format la contient (le numéro repart chaque année). */
  public String cleSequence(int annee) {
    return avecAnnee ? String.valueOf(annee) : "*";
  }

  /**
   * @throws sn.ecole.shared.domain.ErreurMetier SEQUENCE_MATRICULE_EPUISEE si le numéro dépasse le
   *     nombre de chiffres du format
   */
  public String generer(int annee, long numero) {
    if (numero < 1 || String.valueOf(numero).length() > chiffres) {
      throw Saisies.conflit(
          "SEQUENCE_MATRICULE_EPUISEE",
          "Plus de numéro disponible pour ce format de matricule : élargissez {SEQ:n}.");
    }
    StringBuilder sb = new StringBuilder();
    for (Partie p : parties) {
      switch (p) {
        case Texte t -> sb.append(t.valeur());
        case Annee a -> sb.append(a.complete() ? annee : String.format("%02d", annee % 100));
        case Sequence s -> sb.append(String.format("%0" + s.chiffres() + "d", numero));
      }
    }
    return sb.toString();
  }

  private int longueurMax() {
    int total = 0;
    for (Partie p : parties) {
      total +=
          switch (p) {
            case Texte t -> t.valeur().length();
            case Annee a -> a.complete() ? 4 : 2;
            case Sequence s -> s.chiffres();
          };
    }
    return total;
  }

  public String format() {
    return format;
  }

  private static sn.ecole.shared.domain.ErreurMetier invalide(String message) {
    return Saisies.invalide("FORMAT_MATRICULE_INVALIDE", message);
  }
}
