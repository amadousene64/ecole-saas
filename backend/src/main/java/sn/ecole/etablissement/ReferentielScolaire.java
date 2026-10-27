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

  record AnneeEnCours(UUID id, String libelle, LocalDate dateDebut, LocalDate dateFin) {}

  record PeriodeScolaire(
      UUID id, UUID cycleId, int numero, String libelle, LocalDate dateDebut, LocalDate dateFin) {}
}
