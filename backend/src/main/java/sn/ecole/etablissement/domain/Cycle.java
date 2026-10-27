package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.etablissement.ModeNotation;
import sn.ecole.etablissement.TypePeriode;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Cycle d'enseignement et ses paramètres de notation (voir {@code docs/bulletins/}). Les cycles
 * viennent du modèle sénégalais à la création de l'école : on les paramètre, on ne les crée pas.
 */
@Entity
@Table(name = "cycle")
public class Cycle extends TenantAwareEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private CodeCycle code;

  @Column(nullable = false, length = 80)
  private String libelle;

  @Enumerated(EnumType.STRING)
  @Column(name = "type_periode", nullable = false, length = 10)
  private TypePeriode typePeriode;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode_notation", nullable = false, length = 12)
  private ModeNotation modeNotation;

  @Column(name = "base_moyenne", nullable = false)
  private int baseMoyenne;

  protected Cycle() {}

  private Cycle(
      CodeCycle code,
      String libelle,
      TypePeriode typePeriode,
      ModeNotation modeNotation,
      int baseMoyenne) {
    this.code = Objects.requireNonNull(code, "code");
    this.libelle = Verif.exiger(libelle, "Le libellé", 80);
    this.typePeriode = Objects.requireNonNull(typePeriode, "typePeriode");
    this.modeNotation = Objects.requireNonNull(modeNotation, "modeNotation");
    this.baseMoyenne = base(baseMoyenne);
    ReglesNotation.verifier(modeNotation, this.baseMoyenne);
  }

  public static Cycle creer(
      CodeCycle code,
      String libelle,
      TypePeriode typePeriode,
      ModeNotation modeNotation,
      int baseMoyenne) {
    return new Cycle(code, libelle, typePeriode, modeNotation, baseMoyenne);
  }

  public void renommer(String libelle) {
    this.libelle = Verif.exiger(libelle, "Le libellé", 80);
  }

  public void parametrer(TypePeriode typePeriode, ModeNotation modeNotation, int baseMoyenne) {
    this.typePeriode = Objects.requireNonNull(typePeriode, "typePeriode");
    this.modeNotation = Objects.requireNonNull(modeNotation, "modeNotation");
    this.baseMoyenne = base(baseMoyenne);
    ReglesNotation.verifier(modeNotation, this.baseMoyenne);
  }

  /** Vrai si les paramètres de notation et de découpage demandés sont ceux déjà en place. */
  public boolean aLesParametres(
      TypePeriode typePeriode, ModeNotation modeNotation, int baseMoyenne) {
    return this.typePeriode == typePeriode
        && this.modeNotation == modeNotation
        && this.baseMoyenne == baseMoyenne;
  }

  /** Base de la moyenne : 10 ou 20 (un niveau peut la surcharger, voir {@link Niveau}). */
  static int base(int valeur) {
    if (valeur != 10 && valeur != 20) {
      throw Verif.invalide("BASE_MOYENNE_INVALIDE", "La base de la moyenne vaut 10 ou 20.");
    }
    return valeur;
  }

  public CodeCycle getCode() {
    return code;
  }

  public String getLibelle() {
    return libelle;
  }

  public TypePeriode getTypePeriode() {
    return typePeriode;
  }

  public ModeNotation getModeNotation() {
    return modeNotation;
  }

  public int getBaseMoyenne() {
    return baseMoyenne;
  }
}
