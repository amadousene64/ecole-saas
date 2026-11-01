package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Option d'enseignement du catalogue de l'école (ex. ESP, espagnol, groupe LV2). Une classe en
 * propose certaines ({@link ClasseOption}) ; l'élève en choisit au plus une par groupe.
 */
@Entity
@Table(name = "option_enseignement")
public class OptionEnseignement extends TenantAwareEntity {

  private static final Pattern GROUPE = Pattern.compile("[A-Z0-9_]{1,20}");

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 80)
  private String libelle;

  @Column(nullable = false, length = 20)
  private String groupe;

  @Column(nullable = false)
  private boolean actif = true;

  protected OptionEnseignement() {}

  private OptionEnseignement(String code, String libelle, String groupe) {
    modifier(code, libelle, groupe, true);
  }

  public static OptionEnseignement creer(String code, String libelle, String groupe) {
    return new OptionEnseignement(code, libelle, groupe);
  }

  public void modifier(String code, String libelle, String groupe, boolean actif) {
    this.code = Verif.code(code, "Le code");
    this.libelle = Verif.exiger(libelle, "Le libellé", 80);
    this.groupe = groupe(groupe);
    this.actif = actif;
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  /** Groupe en majuscules : lettres, chiffres et soulignés, 20 au plus (ex. LV2, ARTS). */
  public static String groupe(String valeur) {
    String propre = Verif.exiger(valeur, "Le groupe", 20).toUpperCase(Locale.ROOT);
    if (!GROUPE.matcher(propre).matches()) {
      throw Verif.invalide(
          "CHAMP_INVALIDE", "Le groupe ne contient que des lettres, des chiffres et « _ ».");
    }
    return propre;
  }

  public String getCode() {
    return code;
  }

  public String getLibelle() {
    return libelle;
  }

  public String getGroupe() {
    return groupe;
  }

  public boolean isActif() {
    return actif;
  }
}
