package sn.ecole.etablissement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lecture de la structure scolaire de l'école courante, pour les autres modules. */
public interface ReferentielScolaire {

  Optional<AnneeEnCours> anneeEnCours();

  /** Périodes de l'année, tous cycles confondus, par cycle puis numéro. */
  List<PeriodeScolaire> periodes(UUID anneeId);

  /**
   * Paramètres de notation de l'année (type de période, mode de notation, base de la moyenne par
   * cycle et par niveau). Pour une année EN_COURS ou CLOTUREE ce sont les paramètres FIGÉS à son
   * ouverture, jamais modifiés ensuite : c'est cette source, et non le modèle des cycles, que les
   * bulletins doivent lire. Pour une année en préparation, ce sont ceux du modèle à cet instant
   * ({@code figes} faux : ils peuvent encore changer).
   *
   * <p>Un niveau absent de l'année figée est une anomalie, jamais une invitation à lire le modèle
   * des cycles (modifiable) : le bulletin doit échouer.
   *
   * @throws sn.ecole.shared.domain.ErreurMetier ANNEE_INTROUVABLE si l'année n'existe pas
   */
  ParametresNotation parametresNotation(UUID anneeId);

  record AnneeEnCours(UUID id, String libelle, LocalDate dateDebut, LocalDate dateFin) {}

  record PeriodeScolaire(
      UUID id, UUID cycleId, int numero, String libelle, LocalDate dateDebut, LocalDate dateFin) {}

  record ParametresNotation(
      UUID anneeId, boolean figes, List<ParametresCycle> cycles, List<ParametresNiveau> niveaux) {}

  record ParametresCycle(
      UUID cycleId,
      CodeCycle code,
      TypePeriode typePeriode,
      ModeNotation modeNotation,
      int baseMoyenne) {}

  /** {@code baseMoyenne} : la base effective du niveau (sa surcharge, sinon celle de son cycle). */
  record ParametresNiveau(UUID niveauId, UUID cycleId, int baseMoyenne) {}
}
