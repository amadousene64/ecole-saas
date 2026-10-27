package sn.ecole.etablissement.domain;

/** Découpage de l'année d'un cycle. */
public enum TypePeriode {
  TRIMESTRE(3, "trimestre"),
  SEMESTRE(2, "semestre");

  private final int nombre;
  private final String mot;

  TypePeriode(int nombre, String mot) {
    this.nombre = nombre;
    this.mot = mot;
  }

  public int nombre() {
    return nombre;
  }

  /** « 1er trimestre », « 2e semestre »… */
  public String libelle(int numero) {
    return (numero == 1 ? "1er" : numero + "e") + " " + mot;
  }
}
