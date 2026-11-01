package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Code d'activation à usage unique, remis par l'école, qui relie un compte à un responsable (ou à
 * un élève). Seule l'empreinte du secret est stockée. Expire au bout de {@link #VALIDITE} ; épuisé
 * après {@link #TENTATIVES_MAX} essais faux ; révoqué quand un nouveau code est émis pour la même
 * cible.
 */
@Entity
@Table(name = "code_activation")
public class CodeActivation extends TenantAwareEntity {

  public static final Duration VALIDITE = Duration.ofDays(7);
  public static final int TENTATIVES_MAX = 5;

  public enum Cible {
    RESPONSABLE,
    ELEVE
  }

  public enum Statut {
    ACTIF,
    UTILISE,
    REVOQUE,
    EPUISE
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 12)
  private Cible cible;

  @Column(name = "cible_id", nullable = false, updatable = false)
  private UUID cibleId;

  @Column(nullable = false, updatable = false, length = 4)
  private String identifiant;

  @Column(nullable = false, updatable = false)
  private byte[] empreinte;

  @Column(name = "expire_le", nullable = false, updatable = false)
  private Instant expireLe;

  @Column(nullable = false)
  private int tentatives;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Statut statut = Statut.ACTIF;

  @Column(name = "utilise_le")
  private Instant utiliseLe;

  @Column(name = "utilise_par")
  private UUID utilisePar;

  protected CodeActivation() {}

  public static CodeActivation emettre(
      Cible cible, UUID cibleId, String identifiant, byte[] empreinte, Instant maintenant) {
    CodeActivation c = new CodeActivation();
    c.cible = Objects.requireNonNull(cible);
    c.cibleId = Objects.requireNonNull(cibleId);
    c.identifiant = Objects.requireNonNull(identifiant);
    c.empreinte = Objects.requireNonNull(empreinte).clone();
    c.expireLe = maintenant.plus(VALIDITE);
    return c;
  }

  /** Utilisable : actif et non expiré. */
  public boolean utilisable(Instant maintenant) {
    return statut == Statut.ACTIF && maintenant.isBefore(expireLe);
  }

  /** Essai faux : au {@link #TENTATIVES_MAX}-ième, le code est épuisé. */
  public void enregistrerEchec() {
    if (statut != Statut.ACTIF) {
      return;
    }
    tentatives++;
    if (tentatives >= TENTATIVES_MAX) {
      statut = Statut.EPUISE;
    }
  }

  public void utiliser(UUID utilisateur, Instant maintenant) {
    if (!utilisable(maintenant)) {
      throw new IllegalStateException("Code non utilisable.");
    }
    statut = Statut.UTILISE;
    utiliseLe = maintenant;
    utilisePar = Objects.requireNonNull(utilisateur);
  }

  public void revoquer() {
    if (statut == Statut.ACTIF) {
      statut = Statut.REVOQUE;
    }
  }

  public Cible getCible() {
    return cible;
  }

  public UUID getCibleId() {
    return cibleId;
  }

  public String getIdentifiant() {
    return identifiant;
  }

  public byte[] getEmpreinte() {
    return empreinte.clone();
  }

  public Instant getExpireLe() {
    return expireLe;
  }

  public int getTentatives() {
    return tentatives;
  }

  public Statut getStatut() {
    return statut;
  }

  public UUID getUtilisePar() {
    return utilisePar;
  }
}
