package sn.ecole.statistique.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;

/**
 * Tableau de bord de la direction pour l'année en cours : des nombres, jamais d'élève. Valeur
 * gardée dans le cache (JSON) : elle est immuable et sans type propre à une exécution.
 *
 * @param anneeId année en cours, ou null s'il n'y en a pas (tout est alors vide)
 * @param calculeLe instant du calcul : l'écran l'affiche, car le cache dure cinq minutes
 * @param cycle filtre appliqué, ou null pour tous les cycles
 */
public record TableauDeBord(
    UUID anneeId,
    String anneeLibelle,
    Instant calculeLe,
    PeriodeCourbe periode,
    CodeCycle cycle,
    long inscrits,
    List<EffectifCycle> parCycle,
    List<EffectifNiveau> parNiveau,
    List<EffectifStatut> parStatut,
    Part dossiers,
    Part familles,
    List<Semaine> courbe) {

  public TableauDeBord {
    parCycle = List.copyOf(parCycle);
    parNiveau = List.copyOf(parNiveau);
    parStatut = List.copyOf(parStatut);
    courbe = List.copyOf(courbe);
  }

  /** Effectif (inscrits) d'un cycle. */
  public record EffectifCycle(CodeCycle cycle, String libelle, long nombre) {}

  /** Effectif (inscrits) d'un niveau, dans l'ordre des niveaux. */
  public record EffectifNiveau(
      UUID niveauId, String code, String libelle, CodeCycle cycle, long nombre) {}

  /** Nombre d'inscriptions de l'année dans un statut (tous les statuts sont présents). */
  public record EffectifStatut(String statut, long nombre) {}

  /**
   * Une part d'un ensemble.
   *
   * @param total taille de l'ensemble
   * @param dont éléments retenus (dossiers complets, familles connectées)
   * @param pourcentage entier de 0 à 100, arrondi à l'unité ; 0 si l'ensemble est vide
   */
  public record Part(long total, long dont, int pourcentage) {

    public static Part de(long total, long dont) {
      int pct = total <= 0 ? 0 : (int) ((dont * 100 + total / 2) / total);
      return new Part(total, dont, pct);
    }
  }

  /** Inscriptions enregistrées pendant la semaine qui commence ce lundi. */
  public record Semaine(LocalDate lundi, long nombre) {}
}
