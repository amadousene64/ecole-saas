package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Un élève dans une classe pour une année scolaire. Tout le reste (notes, absences, billets, frais)
 * se rattachera à l'inscription ; l'historique d'un élève n'est jamais écrasé.
 *
 * <p>Transitions : PRE_INSCRIT → INSCRIT ; PRE_INSCRIT ou INSCRIT → ABANDON ; INSCRIT → TRANSFERE
 * ou RADIE. Une sortie est définitive.
 */
@Entity
@Table(name = "inscription")
public class Inscription extends TenantAwareEntity {

  @Column(name = "eleve_id", nullable = false, updatable = false)
  private UUID eleveId;

  @Column(name = "classe_id", nullable = false)
  private UUID classeId;

  @Column(name = "annee_id", nullable = false, updatable = false)
  private UUID anneeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private StatutInscription statut;

  @Column(nullable = false)
  private boolean redoublant;

  @Column(name = "date_entree", nullable = false)
  private LocalDate dateEntree;

  @Column(name = "date_sortie")
  private LocalDate dateSortie;

  @Column(name = "motif_sortie", length = 200)
  private String motifSortie;

  protected Inscription() {}

  /**
   * @param statut PRE_INSCRIT ou INSCRIT
   */
  public static Inscription creer(
      UUID eleveId,
      UUID classeId,
      UUID anneeId,
      StatutInscription statut,
      boolean redoublant,
      LocalDate dateEntree) {
    if (statut == null || !statut.actif()) {
      throw Saisies.invalide("STATUT_INVALIDE", "Une inscription commence PRE_INSCRIT ou INSCRIT.");
    }
    Inscription i = new Inscription();
    i.eleveId = Objects.requireNonNull(eleveId);
    i.classeId = Objects.requireNonNull(classeId);
    i.anneeId = Objects.requireNonNull(anneeId);
    i.statut = statut;
    i.redoublant = redoublant;
    i.dateEntree = Objects.requireNonNull(dateEntree);
    return i;
  }

  /** PRE_INSCRIT → INSCRIT. */
  public void valider() {
    if (statut != StatutInscription.PRE_INSCRIT) {
      throw transitionInterdite();
    }
    statut = StatutInscription.INSCRIT;
  }

  /** Changement de classe dans la même année (la capacité est contrôlée par le service). */
  public void changerClasse(UUID nouvelleClasse) {
    exigerActive();
    this.classeId = Objects.requireNonNull(nouvelleClasse);
  }

  public void definirRedoublant(boolean redoublant) {
    exigerActive();
    this.redoublant = redoublant;
  }

  /** Sortie définitive : transfert, radiation ou abandon. */
  public void sortir(StatutInscription sortie, LocalDate date, String motif) {
    if (sortie == null || !StatutInscription.SORTIES.contains(sortie)) {
      throw Saisies.invalide("STATUT_INVALIDE", "Sortie : TRANSFERE, RADIE ou ABANDON.");
    }
    exigerActive();
    if (sortie != StatutInscription.ABANDON && statut != StatutInscription.INSCRIT) {
      // Un pré-inscrit qui ne vient pas abandonne ; on ne radie ni ne transfère qu'un inscrit.
      throw transitionInterdite();
    }
    if (date == null || date.isBefore(dateEntree)) {
      throw Saisies.invalide(
          "DATE_SORTIE_INVALIDE", "La date de sortie est obligatoire et suit la date d'entrée.");
    }
    this.statut = sortie;
    this.dateSortie = date;
    this.motifSortie = Saisies.optionnel(motif, "Le motif", 200);
  }

  public boolean active() {
    return statut.actif();
  }

  private void exigerActive() {
    if (!active()) {
      throw Saisies.conflit(
          "INSCRIPTION_TERMINEE", "Cette inscription est terminée : elle ne change plus.");
    }
  }

  private sn.ecole.shared.domain.ErreurMetier transitionInterdite() {
    return Saisies.conflit(
        "TRANSITION_INTERDITE", "Changement de statut impossible depuis " + statut + ".");
  }

  public UUID getEleveId() {
    return eleveId;
  }

  public UUID getClasseId() {
    return classeId;
  }

  public UUID getAnneeId() {
    return anneeId;
  }

  public StatutInscription getStatut() {
    return statut;
  }

  public boolean isRedoublant() {
    return redoublant;
  }

  public LocalDate getDateEntree() {
    return dateEntree;
  }

  public LocalDate getDateSortie() {
    return dateSortie;
  }

  public String getMotifSortie() {
    return motifSortie;
  }
}
