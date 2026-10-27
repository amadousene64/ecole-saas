package sn.ecole.etablissement.domain;

import java.util.List;
import java.util.Map;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.etablissement.ModeNotation;
import sn.ecole.etablissement.TypePeriode;

/**
 * Référentiel « modèle sénégalais » : cycles, niveaux (CI à Terminale) et séries du lycée. Copié
 * dans chaque école à sa création, puis modifiable par l'école. Constante de code : elle évolue
 * avec les versions de l'application, sans table de référentiel à tenir à jour.
 */
public final class ModeleSenegalais {

  public record ModeleCycle(
      CodeCycle code,
      String libelle,
      TypePeriode typePeriode,
      ModeNotation modeNotation,
      int baseMoyenne) {}

  public record ModeleNiveau(String code, String libelle, CodeCycle cycle, int ordre) {}

  public record ModeleSerie(String code, String libelle) {}

  public static final List<ModeleCycle> CYCLES =
      List.of(
          new ModeleCycle(
              CodeCycle.ELEMENTAIRE, "Élémentaire", TypePeriode.TRIMESTRE, ModeNotation.BAREME, 10),
          new ModeleCycle(
              CodeCycle.MOYEN, "Moyen", TypePeriode.SEMESTRE, ModeNotation.COEFFICIENT, 20),
          new ModeleCycle(
              CodeCycle.SECONDAIRE,
              "Secondaire",
              TypePeriode.SEMESTRE,
              ModeNotation.COEFFICIENT,
              20));

  public static final List<ModeleNiveau> NIVEAUX =
      List.of(
          new ModeleNiveau("CI", "CI", CodeCycle.ELEMENTAIRE, 1),
          new ModeleNiveau("CP", "CP", CodeCycle.ELEMENTAIRE, 2),
          new ModeleNiveau("CE1", "CE1", CodeCycle.ELEMENTAIRE, 3),
          new ModeleNiveau("CE2", "CE2", CodeCycle.ELEMENTAIRE, 4),
          new ModeleNiveau("CM1", "CM1", CodeCycle.ELEMENTAIRE, 5),
          new ModeleNiveau("CM2", "CM2", CodeCycle.ELEMENTAIRE, 6),
          new ModeleNiveau("6E", "6e", CodeCycle.MOYEN, 7),
          new ModeleNiveau("5E", "5e", CodeCycle.MOYEN, 8),
          new ModeleNiveau("4E", "4e", CodeCycle.MOYEN, 9),
          new ModeleNiveau("3E", "3e", CodeCycle.MOYEN, 10),
          new ModeleNiveau("2NDE", "2nde", CodeCycle.SECONDAIRE, 11),
          new ModeleNiveau("1RE", "1re", CodeCycle.SECONDAIRE, 12),
          new ModeleNiveau("TLE", "Tle", CodeCycle.SECONDAIRE, 13));

  public static final List<ModeleSerie> SERIES =
      List.of(
          new ModeleSerie("L", "Série L"),
          new ModeleSerie("S", "Série S"),
          new ModeleSerie("L1", "Série L1"),
          new ModeleSerie("L2", "Série L2"),
          new ModeleSerie("S1", "Série S1"),
          new ModeleSerie("S2", "Série S2"),
          new ModeleSerie("S3", "Série S3"));

  /** Séries proposées par niveau (code du niveau vers codes des séries). */
  public static final Map<String, List<String>> SERIES_PAR_NIVEAU =
      Map.of(
          "2NDE", List.of("L", "S"),
          "1RE", List.of("L1", "L2", "S1", "S2", "S3"),
          "TLE", List.of("L1", "L2", "S1", "S2", "S3"));

  private ModeleSenegalais() {}
}
