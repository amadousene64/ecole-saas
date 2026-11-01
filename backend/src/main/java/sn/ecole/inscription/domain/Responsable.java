package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import sn.ecole.shared.domain.Telephone;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Parent ou tuteur, propre à une école (jamais partagé entre écoles). Un compte Keycloak peut lui
 * être relié par un code d'activation remis par l'école : un parent ne se relie jamais lui-même.
 */
@Entity
@Table(name = "responsable")
public class Responsable extends TenantAwareEntity {

  private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}");

  @Column(nullable = false, length = 100)
  private String prenoms;

  @Column(nullable = false, length = 60)
  private String nom;

  @Column(nullable = false, length = 16)
  private String telephone;

  @Column(length = 254)
  private String email;

  @Column(length = 100)
  private String profession;

  @Column(name = "nom_recherche", nullable = false, length = 200)
  private String nomRecherche;

  @Column(name = "utilisateur_id")
  private UUID utilisateurId;

  protected Responsable() {}

  public static Responsable creer(
      String prenoms, String nom, String telephone, String email, String profession) {
    Responsable r = new Responsable();
    r.modifier(prenoms, nom, telephone, email, profession);
    return r;
  }

  public void modifier(
      String prenoms, String nom, String telephone, String email, String profession) {
    this.prenoms = Saisies.nomPersonne(prenoms, "Les prénoms", 100);
    this.nom = Saisies.nomPersonne(nom, "Le nom", 60).toUpperCase(Locale.ROOT);
    this.telephone = telephone(telephone);
    this.email = email(email);
    this.profession = Saisies.optionnel(profession, "La profession", 100);
    this.nomRecherche = Saisies.normaliser(this.nom + " " + this.prenoms);
  }

  /** Téléphone obligatoire, normalisé en E.164 (+221 par défaut). */
  public static String telephone(String saisie) {
    String normalise = Telephone.normaliser(saisie);
    if (normalise == null) {
      throw Saisies.invalide("TELEPHONE_INVALIDE", "Le téléphone est obligatoire.");
    }
    return normalise;
  }

  private static String email(String saisie) {
    String propre = Saisies.optionnel(saisie, "L'adresse e-mail", 254);
    if (propre == null) {
      return null;
    }
    propre = propre.toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(propre).matches()) {
      throw Saisies.invalide("EMAIL_INVALIDE", "Adresse e-mail invalide.");
    }
    return propre;
  }

  /** Relie le compte du parent (activation par code). Un seul compte, une seule fois. */
  public void relierCompte(UUID utilisateur) {
    Objects.requireNonNull(utilisateur);
    if (utilisateurId != null && !utilisateurId.equals(utilisateur)) {
      throw Saisies.conflit("COMPTE_DEJA_RELIE", "Un compte est déjà relié à ce responsable.");
    }
    this.utilisateurId = utilisateur;
  }

  /** Détache le compte (perte d'accès, changement de téléphone) ; renvoie l'ancien, ou null. */
  public UUID delierCompte() {
    UUID ancien = utilisateurId;
    utilisateurId = null;
    return ancien;
  }

  public String getPrenoms() {
    return prenoms;
  }

  public String getNom() {
    return nom;
  }

  public String getTelephone() {
    return telephone;
  }

  public String getEmail() {
    return email;
  }

  public String getProfession() {
    return profession;
  }

  public String getNomRecherche() {
    return nomRecherche;
  }

  public UUID getUtilisateurId() {
    return utilisateurId;
  }

  @Override
  public String toString() {
    return "Responsable[" + getId() + "]";
  }
}
