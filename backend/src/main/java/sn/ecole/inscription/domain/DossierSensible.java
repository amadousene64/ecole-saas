package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Données sensibles d'un élève (santé, allergies, situation familiale), stockées CHIFFRÉES : cette
 * entité ne manipule que des chiffrés, le service applicatif chiffre et déchiffre. Table à part :
 * jamais chargée avec l'élève, jamais dans une liste.
 */
@Entity
@Table(name = "eleve_dossier_sensible")
public class DossierSensible extends TenantAwareEntity {

  /** Champs du dossier ; leur nom entre dans le contexte du chiffrement. */
  public enum Champ {
    SANTE(2000),
    ALLERGIES(1000),
    SITUATION_FAMILIALE(1000);

    private final int longueurMax;

    Champ(int longueurMax) {
      this.longueurMax = longueurMax;
    }

    /** Longueur maximale du texte clair. */
    public int longueurMax() {
      return longueurMax;
    }
  }

  @Column(name = "eleve_id", nullable = false, updatable = false)
  private UUID eleveId;

  @Column(name = "sante")
  private byte[] sante;

  @Column(name = "allergies")
  private byte[] allergies;

  @Column(name = "situation_familiale")
  private byte[] situationFamiliale;

  protected DossierSensible() {}

  public static DossierSensible pour(UUID eleveId) {
    DossierSensible d = new DossierSensible();
    d.eleveId = Objects.requireNonNull(eleveId);
    return d;
  }

  public byte[] chiffre(Champ champ) {
    return switch (champ) {
      case SANTE -> sante;
      case ALLERGIES -> allergies;
      case SITUATION_FAMILIALE -> situationFamiliale;
    };
  }

  public void remplacer(Champ champ, byte[] chiffre) {
    switch (champ) {
      case SANTE -> sante = chiffre;
      case ALLERGIES -> allergies = chiffre;
      case SITUATION_FAMILIALE -> situationFamiliale = chiffre;
    }
  }

  public boolean renseigne(Champ champ) {
    return chiffre(champ) != null;
  }

  public UUID getEleveId() {
    return eleveId;
  }

  @Override
  public String toString() {
    return "DossierSensible[" + eleveId + "]";
  }
}
