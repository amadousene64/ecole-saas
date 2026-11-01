package sn.ecole.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Événement à relayer vers l'extérieur (SMS, paiement, PDF…), écrit dans la même transaction que le
 * changement métier. Un worker le traitera plus tard, école par école.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends TenantAwareEntity {

  @Column(name = "type_agregat", nullable = false, updatable = false)
  private String typeAgregat;

  @Column(name = "id_agregat", nullable = false, updatable = false)
  private UUID idAgregat;

  @Column(name = "type_evenement", nullable = false, updatable = false)
  private String typeEvenement;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "charge", nullable = false, updatable = false)
  private String charge;

  @Column(name = "traite_le")
  private Instant traiteLe;

  @Column(name = "tentatives", nullable = false)
  private int tentatives;

  @Column(name = "derniere_erreur")
  private String derniereErreur;

  @Column(name = "prochaine_tentative_le", nullable = false)
  private Instant prochaineTentativeLe = Instant.now();

  protected OutboxEvent() {}

  public OutboxEvent(String typeAgregat, UUID idAgregat, String typeEvenement, String chargeJson) {
    this.typeAgregat = Objects.requireNonNull(typeAgregat);
    this.idAgregat = Objects.requireNonNull(idAgregat);
    this.typeEvenement = Objects.requireNonNull(typeEvenement);
    this.charge = Objects.requireNonNull(chargeJson);
  }

  public void marquerTraite(Instant quand) {
    this.traiteLe = quand;
  }

  public void enregistrerEchec(String erreur) {
    enregistrerEchec(erreur, Instant.now());
  }

  /** Échec d'un traitement : nouvelle tentative au plus tôt à {@code prochaine}. */
  public void enregistrerEchec(String erreur, Instant prochaine) {
    this.tentatives++;
    this.derniereErreur = erreur;
    this.prochaineTentativeLe = Objects.requireNonNull(prochaine);
  }

  public Instant getProchaineTentativeLe() {
    return prochaineTentativeLe;
  }

  public String getTypeAgregat() {
    return typeAgregat;
  }

  public UUID getIdAgregat() {
    return idAgregat;
  }

  public String getTypeEvenement() {
    return typeEvenement;
  }

  public String getCharge() {
    return charge;
  }

  public Instant getTraiteLe() {
    return traiteLe;
  }

  public int getTentatives() {
    return tentatives;
  }

  public String getDerniereErreur() {
    return derniereErreur;
  }
}
