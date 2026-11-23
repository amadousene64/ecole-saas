package sn.ecole.personnel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TenantAwareEntity;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Affectation d'un membre du personnel pour une année scolaire : une matière dans une classe
 * (enseignant), une classe de l'élémentaire entière (maître titulaire) ou un cycle (surveillant).
 *
 * <p>Période semi-ouverte [{@code debut}, {@code fin}) : {@code fin} est la première date où
 * l'affectation ne vaut plus ; nulle tant qu'elle est en cours. Un remplacement termine
 * l'affectation de l'un à la date où commence celle de l'autre. Une affectation se termine, elle
 * n'est jamais effacée : l'historique reste.
 */
@Entity
@Table(name = "affectation")
public class Affectation extends TenantAwareEntity {

  @Column(name = "annee_id", nullable = false, updatable = false)
  private UUID anneeId;

  @Column(name = "utilisateur_id", nullable = false, updatable = false)
  private UUID utilisateurId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12, updatable = false)
  private TypeAffectation type;

  @Column(name = "classe_id", updatable = false)
  private UUID classeId;

  @Column(name = "matiere_id", updatable = false)
  private UUID matiereId;

  @Enumerated(EnumType.STRING)
  @Column(name = "code_cycle", length = 12, updatable = false)
  private CodeCycle codeCycle;

  @Column(nullable = false)
  private LocalDate debut;

  @Column private LocalDate fin;

  protected Affectation() {}

  private static Affectation nouvelle(
      UUID anneeId, UUID utilisateurId, TypeAffectation type, LocalDate debut, LocalDate fin) {
    Affectation a = new Affectation();
    a.anneeId = Objects.requireNonNull(anneeId);
    a.utilisateurId = Objects.requireNonNull(utilisateurId);
    a.type = type;
    a.periode(debut, fin);
    return a;
  }

  public static Affectation enseignement(
      UUID anneeId,
      UUID utilisateurId,
      UUID classeId,
      UUID matiereId,
      LocalDate debut,
      LocalDate fin) {
    Affectation a = nouvelle(anneeId, utilisateurId, TypeAffectation.ENSEIGNEMENT, debut, fin);
    a.classeId = Objects.requireNonNull(classeId);
    a.matiereId = Objects.requireNonNull(matiereId);
    return a;
  }

  public static Affectation titulaire(
      UUID anneeId, UUID utilisateurId, UUID classeId, LocalDate debut, LocalDate fin) {
    Affectation a = nouvelle(anneeId, utilisateurId, TypeAffectation.TITULAIRE, debut, fin);
    a.classeId = Objects.requireNonNull(classeId);
    return a;
  }

  public static Affectation surveillance(
      UUID anneeId, UUID utilisateurId, CodeCycle codeCycle, LocalDate debut, LocalDate fin) {
    Affectation a = nouvelle(anneeId, utilisateurId, TypeAffectation.SURVEILLANCE, debut, fin);
    a.codeCycle = Objects.requireNonNull(codeCycle);
    return a;
  }

  /** Change la période (dates mal saisies, remplacement prévu). */
  public void changerPeriode(LocalDate debut, LocalDate fin) {
    periode(debut, fin);
  }

  /**
   * Termine l'affectation à {@code fin} (première date où elle ne vaut plus). Une fin à aujourd'hui
   * prend effet tout de suite.
   */
  public void terminer(LocalDate fin) {
    Objects.requireNonNull(fin);
    // Une fin prévue plus tard peut être avancée ; une fin déjà atteinte à cette date, non.
    if (this.fin != null && !this.fin.isAfter(fin)) {
      throw new ErreurMetier(
          "AFFECTATION_TERMINEE", TypeErreur.CONFLIT, "Cette affectation est déjà terminée.");
    }
    periode(debut, fin);
  }

  private void periode(LocalDate debut, LocalDate fin) {
    if (debut == null) {
      throw new ErreurMetier(
          "PERIODE_INVALIDE", TypeErreur.INVALIDE, "La date de début est obligatoire.");
    }
    if (fin != null && !fin.isAfter(debut)) {
      throw new ErreurMetier(
          "PERIODE_INVALIDE", TypeErreur.INVALIDE, "La fin doit suivre le début de l'affectation.");
    }
    this.debut = debut;
    this.fin = fin;
  }

  /** Annulée avant d'avoir commencé : elle n'a jamais valu (suppression logique, journalisée). */
  public void marquerAnnulee(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  /** En vigueur ce jour-là : début atteint, fin pas encore atteinte. */
  public boolean estEnCoursLe(LocalDate jour) {
    return !debut.isAfter(jour) && (fin == null || fin.isAfter(jour));
  }

  /** Les deux périodes ont au moins un jour en commun. */
  public boolean chevauche(LocalDate autreDebut, LocalDate autreFin) {
    boolean apresAutre = autreFin != null && !debut.isBefore(autreFin);
    boolean avantAutre = fin != null && !fin.isAfter(autreDebut);
    return !apresAutre && !avantAutre;
  }

  public UUID getAnneeId() {
    return anneeId;
  }

  public UUID getUtilisateurId() {
    return utilisateurId;
  }

  public TypeAffectation getType() {
    return type;
  }

  public UUID getClasseId() {
    return classeId;
  }

  public UUID getMatiereId() {
    return matiereId;
  }

  public CodeCycle getCodeCycle() {
    return codeCycle;
  }

  public LocalDate getDebut() {
    return debut;
  }

  public LocalDate getFin() {
    return fin;
  }
}
