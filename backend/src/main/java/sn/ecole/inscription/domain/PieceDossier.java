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
 * Pièce du dossier d'un élève (acte de naissance, certificat, photo d'identité), stockée dans
 * MinIO. Arrive EN_ANALYSE ; ne devient accessible qu'une fois SAIN après l'analyse antivirus. Une
 * pièce INFECTE est retirée du stockage et ne redevient jamais accessible.
 */
@Entity
@Table(name = "piece_dossier")
public class PieceDossier extends TenantAwareEntity {

  public enum Type {
    ACTE_NAISSANCE,
    CERTIFICAT,
    PHOTO_IDENTITE
  }

  public enum Statut {
    EN_ANALYSE,
    SAIN,
    INFECTE
  }

  @Column(name = "eleve_id", nullable = false, updatable = false)
  private UUID eleveId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private Type type;

  @Column(nullable = false, updatable = false, length = 200)
  private String cle;

  @Column(name = "content_type", nullable = false, updatable = false, length = 30)
  private String contentType;

  @Column(nullable = false, updatable = false)
  private int taille;

  @Column(name = "empreinte_sha256", nullable = false, updatable = false, length = 64)
  private String empreinteSha256;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Statut statut = Statut.EN_ANALYSE;

  @Column(name = "analyse_le")
  private Instant analyseLe;

  protected PieceDossier() {}

  public static PieceDossier deposer(
      UUID eleveId, Type type, String cle, ControlePiece.PieceValidee piece) {
    PieceDossier p = new PieceDossier();
    p.eleveId = Objects.requireNonNull(eleveId);
    p.type = Objects.requireNonNull(type);
    p.cle = Objects.requireNonNull(cle);
    p.contentType = piece.contentType();
    p.taille = piece.taille();
    p.empreinteSha256 = piece.sha256();
    return p;
  }

  /** Résultat de l'analyse ; sans effet si la pièce a déjà été analysée (rejeu). */
  public boolean enregistrerAnalyse(boolean saine, Instant quand) {
    if (statut != Statut.EN_ANALYSE) {
      return false;
    }
    statut = saine ? Statut.SAIN : Statut.INFECTE;
    analyseLe = Objects.requireNonNull(quand);
    return true;
  }

  public boolean disponible() {
    return statut == Statut.SAIN;
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  /** Nom proposé au téléchargement, sans aucune donnée personnelle. */
  public String nomFichier() {
    return type.name().toLowerCase(java.util.Locale.ROOT)
        + "."
        + ControlePiece.extension(contentType);
  }

  public UUID getEleveId() {
    return eleveId;
  }

  public Type getType() {
    return type;
  }

  public String getCle() {
    return cle;
  }

  public String getContentType() {
    return contentType;
  }

  public int getTaille() {
    return taille;
  }

  public String getEmpreinteSha256() {
    return empreinteSha256;
  }

  public Statut getStatut() {
    return statut;
  }

  public Instant getAnalyseLe() {
    return analyseLe;
  }
}
