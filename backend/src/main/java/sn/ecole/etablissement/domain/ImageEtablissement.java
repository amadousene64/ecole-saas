package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Logo, cachet ou signature : métadonnées d'un fichier stocké dans MinIO. La clé d'objet est
 * toujours construite par le serveur, jamais fournie par le client.
 */
@Entity
@Table(name = "etablissement_image")
public class ImageEtablissement extends TenantAwareEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private TypeImage type;

  @Column(nullable = false, length = 200)
  private String cle;

  @Column(name = "content_type", nullable = false, length = 30)
  private String contentType;

  @Column(nullable = false)
  private int taille;

  protected ImageEtablissement() {}

  private ImageEtablissement(TypeImage type, String cle, String contentType, int taille) {
    this.type = Objects.requireNonNull(type, "type");
    remplacer(cle, contentType, taille);
  }

  public static ImageEtablissement deposer(
      TypeImage type, String cle, String contentType, int taille) {
    return new ImageEtablissement(type, cle, contentType, taille);
  }

  public void remplacer(String cle, String contentType, int taille) {
    this.cle = Objects.requireNonNull(cle, "cle");
    this.contentType = Objects.requireNonNull(contentType, "contentType");
    this.taille = taille;
  }

  /** Suppression logique : le fichier, lui, est effacé du stockage. */
  public void retirer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public TypeImage getType() {
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
}
