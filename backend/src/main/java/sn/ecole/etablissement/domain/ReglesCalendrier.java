package sn.ecole.etablissement.domain;

import java.time.LocalDate;
import java.util.List;

/** Règles du calendrier d'une année : dans ses dates ; les vacances ne se chevauchent pas. */
public final class ReglesCalendrier {

  private ReglesCalendrier() {}

  /** {@code autres} : les autres événements de l'année (sans celui qu'on modifie). */
  public static void verifier(
      AnneeScolaire annee,
      TypeEvenement type,
      LocalDate debut,
      LocalDate fin,
      List<EvenementCalendrier> autres) {
    if (!annee.contient(debut) || !annee.contient(fin)) {
      throw Verif.regle(
          "EVENEMENT_HORS_ANNEE", "L'événement doit rester dans les dates de l'année.");
    }
    if (type == TypeEvenement.VACANCES
        && autres.stream()
            .anyMatch(e -> e.getType() == TypeEvenement.VACANCES && e.chevauche(debut, fin))) {
      throw Verif.regle("VACANCES_CHEVAUCHANTES", "Ces vacances en chevauchent d'autres.");
    }
  }
}
