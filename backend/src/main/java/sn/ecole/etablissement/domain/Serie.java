package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Série du lycée (L1, S2…). Les niveaux qui la proposent sont dans {@link NiveauSerie}. */
@Entity
@Table(name = "serie")
public class Serie extends TenantAwareEntity {

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 80)
  private String libelle;

  @Column(nullable = false)
  private boolean actif = true;

  protected Serie() {}

  private Serie(String code, String libelle) {
    this.code = Verif.code(code, "Le code");
    this.libelle = Verif.exiger(libelle, "Le libellé", 80);
  }

  public static Serie creer(String code, String libelle) {
    return new Serie(code, libelle);
  }

  public void modifier(String code, String libelle, boolean actif) {
    this.code = Verif.code(code, "Le code");
    this.libelle = Verif.exiger(libelle, "Le libellé", 80);
    this.actif = actif;
  }

  public void supprimer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public String getCode() {
    return code;
  }

  public String getLibelle() {
    return libelle;
  }

  public boolean isActif() {
    return actif;
  }
}
