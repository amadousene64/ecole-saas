package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Créneau de la grille horaire d'un cycle (08:00 – 10:00, du lundi au vendredi). L'emploi du temps
 * y placera ensuite classes, matières et enseignants. Bornes : de 6 h à 20 h, 15 minutes au moins.
 * Deux créneaux d'un même cycle ne se chevauchent pas un même jour (contrôlé par le service).
 */
@Entity
@Table(name = "creneau")
public class Creneau extends TenantAwareEntity {

  static final LocalTime OUVERTURE = LocalTime.of(6, 0);
  static final LocalTime FERMETURE = LocalTime.of(20, 0);
  static final int DUREE_MIN_MINUTES = 15;

  @Column(name = "cycle_id", nullable = false, updatable = false)
  private UUID cycleId;

  @Column(nullable = false)
  private LocalTime debut;

  @Column(nullable = false)
  private LocalTime fin;

  /** Lundi = 1, mardi = 2, mercredi = 4… dimanche = 64. */
  @Column(nullable = false)
  private short jours;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TypeCreneau type;

  @Column(length = 30)
  private String libelle;

  protected Creneau() {}

  public static Creneau creer(
      UUID cycleId,
      LocalTime debut,
      LocalTime fin,
      Collection<DayOfWeek> jours,
      TypeCreneau type,
      String libelle) {
    Creneau c = new Creneau();
    c.cycleId = Objects.requireNonNull(cycleId);
    c.renseigner(debut, fin, jours, type, libelle);
    return c;
  }

  /** Même horaire, mêmes jours, dans un autre cycle (copie de grille). */
  public Creneau copierVers(UUID autreCycle) {
    return creer(autreCycle, debut, fin, getJours(), type, libelle);
  }

  public void modifier(
      LocalTime debut,
      LocalTime fin,
      Collection<DayOfWeek> jours,
      TypeCreneau type,
      String libelle) {
    renseigner(debut, fin, jours, type, libelle);
  }

  private void renseigner(
      LocalTime debut,
      LocalTime fin,
      Collection<DayOfWeek> jours,
      TypeCreneau type,
      String libelle) {
    if (debut == null || fin == null) {
      throw Verif.invalide(
          "HORAIRE_INVALIDE", "L'heure de début et l'heure de fin sont obligatoires.");
    }
    LocalTime d = debut.withSecond(0).withNano(0);
    LocalTime f = fin.withSecond(0).withNano(0);
    if (d.isBefore(OUVERTURE) || f.isAfter(FERMETURE)) {
      throw Verif.invalide("HORAIRE_INVALIDE", "Un créneau se situe entre 6 h et 20 h.");
    }
    // Durée calculée sans repasser par minuit : 23:50 → 08:00 est refusé (début après la fin).
    if (!d.isBefore(f) || Duration.between(d, f).toMinutes() < DUREE_MIN_MINUTES) {
      throw Verif.invalide(
          "HORAIRE_INVALIDE",
          "Un créneau finit après son début et dure au moins " + DUREE_MIN_MINUTES + " minutes.");
    }
    if (jours == null || jours.isEmpty()) {
      throw Verif.invalide("JOURS_REQUIS", "Choisissez au moins un jour.");
    }
    this.debut = d;
    this.fin = f;
    this.jours = masque(jours);
    this.type = Objects.requireNonNullElse(type, TypeCreneau.COURS);
    this.libelle = Verif.optionnel(libelle, "Le libellé", 30);
  }

  /** Les deux créneaux ont un jour en commun et leurs horaires se recouvrent. */
  public boolean chevauche(Creneau autre) {
    return (jours & autre.jours) != 0 && debut.isBefore(autre.fin) && autre.debut.isBefore(fin);
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  private static short masque(Collection<DayOfWeek> jours) {
    int m = 0;
    for (DayOfWeek j : jours) {
      m |= 1 << (j.getValue() - 1);
    }
    return (short) m;
  }

  public UUID getCycleId() {
    return cycleId;
  }

  public LocalTime getDebut() {
    return debut;
  }

  public LocalTime getFin() {
    return fin;
  }

  public Set<DayOfWeek> getJours() {
    EnumSet<DayOfWeek> resultat = EnumSet.noneOf(DayOfWeek.class);
    for (DayOfWeek j : DayOfWeek.values()) {
      if ((jours & (1 << (j.getValue() - 1))) != 0) {
        resultat.add(j);
      }
    }
    return resultat;
  }

  public TypeCreneau getType() {
    return type;
  }

  public String getLibelle() {
    return libelle;
  }
}
