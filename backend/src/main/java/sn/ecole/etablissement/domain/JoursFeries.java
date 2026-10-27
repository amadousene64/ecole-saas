package sn.ecole.etablissement.domain;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Jours fériés proposés à la création d'une année : dates fixes et fêtes chrétiennes mobiles
 * calculées depuis Pâques. Les fêtes musulmanes dépendent de l'observation de la lune : elles sont
 * saisies à la main, comme les vacances.
 */
public final class JoursFeries {

  /** Un jour férié proposé. */
  public record Ferie(String libelle, LocalDate date) {}

  private JoursFeries() {}

  /** Jours fériés compris dans {@code [debut, fin]}, par date croissante. */
  public static List<Ferie> pour(LocalDate debut, LocalDate fin) {
    List<Ferie> feries = new ArrayList<>();
    for (int annee = debut.getYear(); annee <= fin.getYear(); annee++) {
      feries.add(new Ferie("Jour de l'An", LocalDate.of(annee, Month.JANUARY, 1)));
      feries.add(new Ferie("Fête de l'Indépendance", LocalDate.of(annee, Month.APRIL, 4)));
      feries.add(new Ferie("Fête du Travail", LocalDate.of(annee, Month.MAY, 1)));
      feries.add(new Ferie("Assomption", LocalDate.of(annee, Month.AUGUST, 15)));
      feries.add(new Ferie("Toussaint", LocalDate.of(annee, Month.NOVEMBER, 1)));
      feries.add(new Ferie("Noël", LocalDate.of(annee, Month.DECEMBER, 25)));
      LocalDate paques = paques(annee);
      feries.add(new Ferie("Lundi de Pâques", paques.plusDays(1)));
      feries.add(new Ferie("Ascension", paques.plusDays(39)));
      feries.add(new Ferie("Lundi de Pentecôte", paques.plusDays(50)));
    }
    return feries.stream()
        .filter(f -> !f.date().isBefore(debut) && !f.date().isAfter(fin))
        .sorted(Comparator.comparing(Ferie::date))
        .toList();
  }

  /** Dimanche de Pâques (calendrier grégorien) : algorithme de Meeus, Jones et Butcher. */
  public static LocalDate paques(int annee) {
    int a = annee % 19;
    int b = annee / 100;
    int c = annee % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int mois = (h + l - 7 * m + 114) / 31;
    int jour = (h + l - 7 * m + 114) % 31 + 1;
    return LocalDate.of(annee, mois, jour);
  }
}
