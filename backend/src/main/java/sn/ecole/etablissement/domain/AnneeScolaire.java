package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Année scolaire d'une école : PREPARATION, puis EN_COURS (une seule à la fois), puis CLOTUREE. Une
 * année clôturée est en lecture seule, ainsi que tout ce qui lui est rattaché.
 */
@Entity
@Table(name = "annee_scolaire")
public class AnneeScolaire extends TenantAwareEntity {

  private static final Pattern LIBELLE = Pattern.compile("(\\d{4})-(\\d{4})");

  @Column(nullable = false, length = 9)
  private String libelle;

  @Column(name = "date_debut", nullable = false)
  private LocalDate dateDebut;

  @Column(name = "date_fin", nullable = false)
  private LocalDate dateFin;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private StatutAnnee statut = StatutAnnee.PREPARATION;

  protected AnneeScolaire() {}

  private AnneeScolaire(String libelle, LocalDate dateDebut, LocalDate dateFin) {
    definir(libelle, dateDebut, dateFin);
  }

  public static AnneeScolaire creer(String libelle, LocalDate dateDebut, LocalDate dateFin) {
    return new AnneeScolaire(libelle, dateDebut, dateFin);
  }

  public void modifier(String libelle, LocalDate dateDebut, LocalDate dateFin) {
    verifierModifiable();
    definir(libelle, dateDebut, dateFin);
  }

  /** PREPARATION vers EN_COURS. L'unicité de l'année en cours est contrôlée par le service. */
  public void ouvrir() {
    if (statut != StatutAnnee.PREPARATION) {
      throw transitionInterdite("ouvrir");
    }
    statut = StatutAnnee.EN_COURS;
  }

  /** EN_COURS vers CLOTUREE : définitif. */
  public void cloturer() {
    if (statut != StatutAnnee.EN_COURS) {
      throw transitionInterdite("clôturer");
    }
    statut = StatutAnnee.CLOTUREE;
  }

  /** Seule une année encore en préparation, donc sans données, peut disparaître. */
  public void supprimer(Instant quand) {
    if (statut != StatutAnnee.PREPARATION) {
      throw Verif.conflit(
          "ANNEE_NON_SUPPRIMABLE", "Seule une année en préparation peut être supprimée.");
    }
    marquerSupprime(Objects.requireNonNull(quand));
  }

  /** À appeler avant toute modification de l'année ou de ce qui lui est rattaché. */
  public void verifierModifiable() {
    if (statut == StatutAnnee.CLOTUREE) {
      throw Verif.conflit("ANNEE_CLOTUREE", "L'année scolaire est clôturée : lecture seule.");
    }
  }

  public boolean contient(LocalDate jour) {
    return !jour.isBefore(dateDebut) && !jour.isAfter(dateFin);
  }

  private void definir(String libelle, LocalDate dateDebut, LocalDate dateFin) {
    String propre = Verif.exiger(libelle, "Le libellé", 9);
    Matcher m = LIBELLE.matcher(propre);
    if (!m.matches() || Integer.parseInt(m.group(2)) != Integer.parseInt(m.group(1)) + 1) {
      throw Verif.invalide(
          "LIBELLE_ANNEE_INVALIDE", "Le libellé d'une année s'écrit AAAA-AAAA+1 (ex. 2026-2027).");
    }
    Objects.requireNonNull(dateDebut, "dateDebut");
    Objects.requireNonNull(dateFin, "dateFin");
    if (!dateDebut.isBefore(dateFin)
        || dateDebut.getYear() != Integer.parseInt(m.group(1))
        || dateFin.getYear() != Integer.parseInt(m.group(2))) {
      throw Verif.invalide(
          "DATES_ANNEE_INVALIDES",
          "Les dates doivent correspondre au libellé : début en "
              + m.group(1)
              + ", fin en "
              + m.group(2)
              + ".");
    }
    this.libelle = propre;
    this.dateDebut = dateDebut;
    this.dateFin = dateFin;
  }

  private ErreurMetier transitionInterdite(String action) {
    return Verif.conflit(
        "TRANSITION_ANNEE_INTERDITE", "Impossible de " + action + " une année « " + statut + " ».");
  }

  public String getLibelle() {
    return libelle;
  }

  public LocalDate getDateDebut() {
    return dateDebut;
  }

  public LocalDate getDateFin() {
    return dateFin;
  }

  public StatutAnnee getStatut() {
    return statut;
  }
}
