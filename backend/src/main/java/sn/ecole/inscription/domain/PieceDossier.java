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
 * Pièce du dossier d'un élève (acte de naissance, certificats, photo d'identité), stockée dans
 * MinIO. Arrive EN_ANALYSE ; ne devient accessible qu'une fois SAIN après l'analyse antivirus. Une
 * pièce INFECTE est retirée du stockage et ne redevient jamais accessible.
 */
@Entity
@Table(name = "piece_dossier")
public class PieceDossier extends TenantAwareEntity {

  public enum Type {
    ACTE_NAISSANCE,
    CERTIFICAT_SCOLARITE,
    /** Donnée de santé : lue par DIRECTEUR et CENSEUR seulement, chaque lecture journalisée. */
    CERTIFICAT_MEDICAL,
    PHOTO_IDENTITE
  }

  /** Pièces exigées pour un dossier complet : liste fixe (issue #42 : paramétrable par école). */
  public static final java.util.List<Type> EXIGEES =
      java.util.List.of(Type.ACTE_NAISSANCE, Type.PHOTO_IDENTITE);

  public enum Statut {
    EN_ANALYSE,
    SAIN,
    INFECTE
  }

  /** Élève du dossier ; {@code null} tant que la pièce est rattachée à un brouillon. */
  @Column(name = "eleve_id")
  private UUID eleveId;

  /** Brouillon d'inscription, jusqu'à sa finalisation (exactement un des deux rattachements). */
  @Column(name = "brouillon_id")
  private UUID brouillonId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 30)
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

  /** Pièce déposée pendant l'assistant d'inscription, avant que l'élève n'existe. */
  public static PieceDossier deposerPourBrouillon(
      UUID brouillonId, Type type, String cle, ControlePiece.PieceValidee piece) {
    PieceDossier p = new PieceDossier();
    p.brouillonId = Objects.requireNonNull(brouillonId);
    p.type = Objects.requireNonNull(type);
    p.cle = Objects.requireNonNull(cle);
    p.contentType = piece.contentType();
    p.taille = piece.taille();
    p.empreinteSha256 = piece.sha256();
    return p;
  }

  /** Finalisation du brouillon : la pièce passe au dossier de l'élève créé. */
  public void rattacherA(UUID eleve) {
    if (brouillonId == null) {
      throw new IllegalStateException("Pièce déjà rattachée à un élève.");
    }
    eleveId = Objects.requireNonNull(eleve);
    brouillonId = null;
  }

  /** Type de l'agrégat auquel la pièce appartient (élève ou brouillon), pour le journal. */
  public String typeRattachement() {
    return eleveId != null ? "Eleve" : "BrouillonInscription";
  }

  public UUID idRattachement() {
    return eleveId != null ? eleveId : brouillonId;
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

  /** Pièce soumise aux règles des données sensibles (santé). */
  public boolean estSensible() {
    return type == Type.CERTIFICAT_MEDICAL;
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

  public UUID getBrouillonId() {
    return brouillonId;
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
