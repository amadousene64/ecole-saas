package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Lien entre un élève et un responsable, avec ses droits. Les fratries se déduisent des
 * responsables communs.
 *
 * <p>{@code accesEspaceParent} (vrai par défaut) ne se modifie que par le directeur : à faux (par
 * exemple sur décision de justice), le responsable ne voit plus l'élève et ne reçoit aucune
 * notification, quels que soient ses autres droits.
 */
@Entity
@Table(name = "lien_parental")
public class LienParental extends TenantAwareEntity {

  /** Droits portés par le lien (hors accès à l'espace parent, réservé au directeur). */
  public record Droits(
      boolean responsableLegal,
      boolean payeur,
      boolean contactUrgence,
      boolean autoriseRecuperer,
      boolean recoitNotifications) {}

  @Column(name = "eleve_id", nullable = false, updatable = false)
  private UUID eleveId;

  @Column(name = "responsable_id", nullable = false, updatable = false)
  private UUID responsableId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 6)
  private TypeLien type;

  @Column(name = "responsable_legal", nullable = false)
  private boolean responsableLegal;

  @Column(nullable = false)
  private boolean payeur;

  @Column(name = "contact_urgence", nullable = false)
  private boolean contactUrgence;

  @Column(name = "autorise_recuperer", nullable = false)
  private boolean autoriseRecuperer;

  @Column(name = "recoit_notifications", nullable = false)
  private boolean recoitNotifications;

  @Column(name = "acces_espace_parent", nullable = false)
  private boolean accesEspaceParent = true;

  protected LienParental() {}

  public static LienParental creer(UUID eleveId, UUID responsableId, TypeLien type, Droits droits) {
    LienParental lien = new LienParental();
    lien.eleveId = Objects.requireNonNull(eleveId);
    lien.responsableId = Objects.requireNonNull(responsableId);
    lien.modifier(type, droits);
    return lien;
  }

  public void modifier(TypeLien type, Droits droits) {
    this.type = Objects.requireNonNull(type, "type");
    Objects.requireNonNull(droits, "droits");
    if (droits.recoitNotifications() && !accesEspaceParent) {
      throw Saisies.regle(
          "NOTIFICATIONS_INTERDITES",
          "Ce responsable n'a plus accès à l'espace parent : il ne peut pas recevoir de"
              + " notifications.");
    }
    this.responsableLegal = droits.responsableLegal();
    this.payeur = droits.payeur();
    this.contactUrgence = droits.contactUrgence();
    this.autoriseRecuperer = droits.autoriseRecuperer();
    this.recoitNotifications = droits.recoitNotifications();
  }

  /** Retirer l'accès coupe aussi les notifications ; le rendre ne les rétablit pas d'office. */
  public void definirAccesEspaceParent(boolean acces) {
    this.accesEspaceParent = acces;
    if (!acces) {
      this.recoitNotifications = false;
    }
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  /** Notifications effectives : jamais sans accès à l'espace parent. */
  public boolean notifie() {
    return accesEspaceParent && recoitNotifications;
  }

  public Droits droits() {
    return new Droits(
        responsableLegal, payeur, contactUrgence, autoriseRecuperer, recoitNotifications);
  }

  public UUID getEleveId() {
    return eleveId;
  }

  public UUID getResponsableId() {
    return responsableId;
  }

  public TypeLien getType() {
    return type;
  }

  public boolean isResponsableLegal() {
    return responsableLegal;
  }

  public boolean isPayeur() {
    return payeur;
  }

  public boolean isContactUrgence() {
    return contactUrgence;
  }

  public boolean isAutoriseRecuperer() {
    return autoriseRecuperer;
  }

  public boolean isRecoitNotifications() {
    return recoitNotifications;
  }

  public boolean isAccesEspaceParent() {
    return accesEspaceParent;
  }
}
