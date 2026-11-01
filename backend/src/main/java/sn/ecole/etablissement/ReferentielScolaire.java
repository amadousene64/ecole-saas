package sn.ecole.etablissement;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lecture de la structure scolaire de l'école courante, pour les autres modules. */
public interface ReferentielScolaire {

  Optional<AnneeEnCours> anneeEnCours();

  /**
   * Une année scolaire, quel que soit son statut.
   *
   * @throws sn.ecole.shared.domain.ErreurMetier ANNEE_INTROUVABLE si elle n'existe pas
   */
  AnneeScolaireInfo annee(UUID anneeId);

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

  /**
   * Une classe : son année, son niveau, sa série éventuelle, le cycle du niveau, sa capacité et les
   * options qu'elle propose. Pour les autres modules (inscription, bulletin) ; le niveau et la
   * série sont figés dès que l'année est en cours. Réservé au personnel.
   *
   * @throws sn.ecole.shared.domain.ErreurMetier CLASSE_INTROUVABLE si elle n'existe pas (ou est
   *     d'une autre école)
   */
  ClasseScolaire classe(UUID classeId);

  /** Classes d'une année, par nom. Réservé au personnel. */
  List<ClasseScolaire> classes(UUID anneeId);

  /**
   * Noms des classes demandées (les inconnues sont absentes). Ouvert à tous les rôles : le module
   * appelant a déjà vérifié que l'utilisateur peut voir l'élève inscrit dans ces classes.
   */
  Map<UUID, String> nomsClasses(Collection<UUID> classeIds);

  /**
   * Niveaux de l'école, par ordre, avec leur cycle. Réservé au personnel (libellés d'affichage des
   * statistiques et des listes).
   */
  List<NiveauScolaire> niveaux();

  record NiveauScolaire(UUID id, String code, String libelle, int ordre, CodeCycle codeCycle) {}

  record ClasseScolaire(
      UUID id,
      UUID anneeId,
      UUID niveauId,
      UUID serieId,
      UUID cycleId,
      CodeCycle codeCycle,
      String nom,
      int capacite,
      List<OptionScolaire> options) {

    public ClasseScolaire {
      options = List.copyOf(options);
    }
  }

  /** Option d'enseignement proposée par une classe ; une seule par groupe pour un élève. */
  record OptionScolaire(UUID id, String code, String libelle, String groupe) {}

  record AnneeEnCours(UUID id, String libelle, LocalDate dateDebut, LocalDate dateFin) {}

  /** {@code cloturee} : lecture seule ; {@code enCours} : l'année ouverte de l'école. */
  record AnneeScolaireInfo(
      UUID id,
      String libelle,
      LocalDate dateDebut,
      LocalDate dateFin,
      boolean enCours,
      boolean cloturee) {}

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
