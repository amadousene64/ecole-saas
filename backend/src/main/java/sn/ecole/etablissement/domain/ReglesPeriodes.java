package sn.ecole.etablissement.domain;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import sn.ecole.etablissement.TypePeriode;

/** Règles d'un jeu de périodes d'un même cycle, et leur génération par défaut. */
public final class ReglesPeriodes {

  private ReglesPeriodes() {}

  /**
   * Découpe l'année en {@code type.nombre()} blocs contigus de durée égale (les jours restants vont
   * aux premiers blocs). L'école ajuste ensuite les dates.
   */
  public static List<PeriodePrevue> generer(AnneeScolaire annee, TypePeriode type) {
    long total = ChronoUnit.DAYS.between(annee.getDateDebut(), annee.getDateFin()) + 1;
    int nombre = type.nombre();
    List<PeriodePrevue> periodes = new ArrayList<>();
    var debut = annee.getDateDebut();
    for (int numero = 1; numero <= nombre; numero++) {
      long duree = total / nombre + (numero <= total % nombre ? 1 : 0);
      var fin = debut.plusDays(duree - 1);
      periodes.add(new PeriodePrevue(numero, debut, fin));
      debut = fin.plusDays(1);
    }
    return periodes;
  }

  /**
   * Chaque période est dans les dates de l'année, a des dates cohérentes, et commence strictement
   * après la fin de la précédente (numéros croissants, donc ordre chronologique). Les trous
   * (vacances) sont permis.
   */
  public static void verifier(AnneeScolaire annee, List<PeriodePrevue> periodes) {
    Set<Integer> numeros = new HashSet<>();
    for (PeriodePrevue p : periodes) {
      if (!numeros.add(p.numero())) {
        throw Verif.regle(
            "PERIODE_EN_DOUBLE", "Deux périodes portent le numéro " + p.numero() + ".");
      }
      if (p.debut().isAfter(p.fin())) {
        throw Verif.regle("PERIODE_INVALIDE", "Une période finit avant d'avoir commencé.");
      }
      if (!annee.contient(p.debut()) || !annee.contient(p.fin())) {
        throw Verif.regle(
            "PERIODE_HORS_ANNEE", "Les périodes doivent rester dans les dates de l'année.");
      }
    }
    List<PeriodePrevue> ordonnees =
        periodes.stream().sorted(Comparator.comparingInt(PeriodePrevue::numero)).toList();
    for (int i = 1; i < ordonnees.size(); i++) {
      if (!ordonnees.get(i).debut().isAfter(ordonnees.get(i - 1).fin())) {
        throw Verif.regle(
            "PERIODES_CHEVAUCHANTES",
            "La période " + ordonnees.get(i).numero() + " chevauche la précédente.");
      }
    }
  }
}
