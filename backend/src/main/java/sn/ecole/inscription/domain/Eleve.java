package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Un élève de l'école. Le matricule est attribué une fois pour toutes ; l'identité se corrige. Les
 * données sensibles sont à part ({@link DossierSensible}), chiffrées.
 */
@Entity
@Table(name = "eleve")
public class Eleve extends TenantAwareEntity {

  @Column(nullable = false, updatable = false, length = 30)
  private String matricule;

  @Column(nullable = false, length = 100)
  private String prenoms;

  @Column(nullable = false, length = 60)
  private String nom;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 1)
  private Sexe sexe;

  @Column(name = "date_naissance", nullable = false)
  private LocalDate dateNaissance;

  @Column(name = "lieu_naissance", nullable = false, length = 100)
  private String lieuNaissance;

  @Column(nullable = false, length = 2)
  private String nationalite;

  @Column(name = "identifiant_national", length = 30)
  private String identifiantNational;

  @Column(name = "photo_cle", length = 200)
  private String photoCle;

  @Column(name = "photo_content_type", length = 30)
  private String photoContentType;

  @Column(name = "nom_recherche", nullable = false, length = 200)
  private String nomRecherche;

  @Column(name = "utilisateur_id")
  private UUID utilisateurId;

  protected Eleve() {}

  private Eleve(String matricule, IdentiteEleve identite) {
    this.matricule = Matricule.verifier(matricule);
    corriger(identite);
  }

  public static Eleve creer(String matricule, IdentiteEleve identite) {
    return new Eleve(matricule, identite);
  }

  public void corriger(IdentiteEleve identite) {
    Objects.requireNonNull(identite, "identite");
    this.prenoms = identite.prenoms();
    this.nom = identite.nom();
    this.sexe = identite.sexe();
    this.dateNaissance = identite.dateNaissance();
    this.lieuNaissance = identite.lieuNaissance();
    this.nationalite = identite.nationalite();
    this.identifiantNational = identite.identifiantNational();
    this.nomRecherche = Saisies.normaliser(nom + " " + prenoms);
  }

  /** Remplace la photo ; renvoie la clé de l'ancienne (à supprimer après validation), ou null. */
  public String changerPhoto(String cle, String contentType) {
    String ancienne = photoCle;
    this.photoCle = Objects.requireNonNull(cle);
    this.photoContentType = Objects.requireNonNull(contentType);
    return ancienne;
  }

  /** Retire la photo ; renvoie la clé à supprimer, ou null s'il n'y en avait pas. */
  public String retirerPhoto() {
    String ancienne = photoCle;
    this.photoCle = null;
    this.photoContentType = null;
    return ancienne;
  }

  /** Relie le compte de l'élève (activation par code). Un seul compte, une seule fois. */
  public void relierCompte(UUID utilisateur) {
    Objects.requireNonNull(utilisateur);
    if (utilisateurId != null && !utilisateurId.equals(utilisateur)) {
      throw Saisies.conflit("COMPTE_DEJA_RELIE", "Un compte est déjà relié à cet élève.");
    }
    this.utilisateurId = utilisateur;
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public IdentiteEleve identite() {
    return new IdentiteEleve(
        prenoms, nom, sexe, dateNaissance, lieuNaissance, nationalite, identifiantNational);
  }

  public String getMatricule() {
    return matricule;
  }

  public String getPrenoms() {
    return prenoms;
  }

  public String getNom() {
    return nom;
  }

  public Sexe getSexe() {
    return sexe;
  }

  public LocalDate getDateNaissance() {
    return dateNaissance;
  }

  public String getLieuNaissance() {
    return lieuNaissance;
  }

  public String getNationalite() {
    return nationalite;
  }

  public String getIdentifiantNational() {
    return identifiantNational;
  }

  public String getPhotoCle() {
    return photoCle;
  }

  public String getPhotoContentType() {
    return photoContentType;
  }

  public String getNomRecherche() {
    return nomRecherche;
  }

  public UUID getUtilisateurId() {
    return utilisateurId;
  }

  /** Jamais de nom dans une trace : l'identifiant et le matricule suffisent. */
  @Override
  public String toString() {
    return "Eleve[" + getId() + ", " + matricule + "]";
  }
}
