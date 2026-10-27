package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;
import java.util.regex.Pattern;
import sn.ecole.etablissement.StatutEtablissement;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Profil de l'école cliente (une seule ligne par école). Le nom et le code sont ceux du profil :
 * ils ne sont pas synchronisés avec la table {@code tenant}, qui identifie l'école pour la
 * plateforme.
 */
@Entity
@Table(name = "etablissement")
public class Etablissement extends TenantAwareEntity {

  public static final String FUSEAU_DEFAUT = "Africa/Dakar";
  public static final String DEVISE_DEFAUT = "XOF";

  private static final Pattern CODE = Pattern.compile("[A-Za-z0-9._-]{1,40}");
  private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

  @Column(nullable = false, length = 200)
  private String nom;

  @Column(nullable = false, length = 40)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private StatutEtablissement statut;

  @Column(length = 120)
  private String ia;

  @Column(length = 120)
  private String ief;

  @Column(length = 300)
  private String adresse;

  @Column(length = 16)
  private String telephone;

  @Column(length = 254)
  private String email;

  @Column(nullable = false, length = 40)
  private String fuseau = FUSEAU_DEFAUT;

  @Column(nullable = false, length = 3)
  private String devise = DEVISE_DEFAUT;

  protected Etablissement() {}

  private Etablissement(String nom, String code, StatutEtablissement statut) {
    appliquer(nom, code, statut);
  }

  /** L'école est celle du contexte courant (renseignée par Hibernate à l'enregistrement). */
  public static Etablissement creer(String nom, String code, StatutEtablissement statut) {
    return new Etablissement(nom, code, statut);
  }

  public void modifier(
      String nom,
      String code,
      StatutEtablissement statut,
      String ia,
      String ief,
      String adresse,
      String telephone,
      String email,
      String fuseau,
      String devise) {
    appliquer(nom, code, statut);
    this.ia = Verif.optionnel(ia, "L'inspection d'académie", 120);
    this.ief = Verif.optionnel(ief, "L'IEF", 120);
    this.adresse = Verif.optionnel(adresse, "L'adresse", 300);
    this.telephone = Telephone.normaliser(telephone);
    this.email = email(email);
    this.fuseau = fuseau(fuseau);
    this.devise = devise(devise);
  }

  private void appliquer(String nom, String code, StatutEtablissement statut) {
    this.nom = Verif.exiger(nom, "Le nom", 200);
    String codePropre = Verif.exiger(code, "Le code", 40);
    if (!CODE.matcher(codePropre).matches()) {
      throw Verif.invalide(
          "CHAMP_INVALIDE", "Le code ne contient que des lettres, chiffres, points et tirets.");
    }
    this.code = codePropre;
    this.statut = Objects.requireNonNull(statut, "statut");
  }

  private static String email(String valeur) {
    String propre = Verif.optionnel(valeur, "L'e-mail", 254);
    if (propre != null && !EMAIL.matcher(propre).matches()) {
      throw Verif.invalide("EMAIL_INVALIDE", "Adresse e-mail invalide.");
    }
    return propre;
  }

  private static String fuseau(String valeur) {
    String propre = valeur == null || valeur.isBlank() ? FUSEAU_DEFAUT : valeur.strip();
    try {
      return ZoneId.of(propre).getId();
    } catch (DateTimeException e) {
      throw Verif.invalide("FUSEAU_INVALIDE", "Fuseau horaire inconnu.");
    }
  }

  private static String devise(String valeur) {
    String propre = valeur == null || valeur.isBlank() ? DEVISE_DEFAUT : valeur.strip();
    try {
      return Currency.getInstance(propre).getCurrencyCode();
    } catch (IllegalArgumentException e) {
      throw Verif.invalide("DEVISE_INVALIDE", "Devise inconnue (code ISO 4217 attendu).");
    }
  }

  public String getNom() {
    return nom;
  }

  public String getCode() {
    return code;
  }

  public StatutEtablissement getStatut() {
    return statut;
  }

  public String getIa() {
    return ia;
  }

  public String getIef() {
    return ief;
  }

  public String getAdresse() {
    return adresse;
  }

  public String getTelephone() {
    return telephone;
  }

  public String getEmail() {
    return email;
  }

  public String getFuseau() {
    return fuseau;
  }

  public String getDevise() {
    return devise;
  }
}
