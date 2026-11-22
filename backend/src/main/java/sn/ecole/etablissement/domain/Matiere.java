package sn.ecole.etablissement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Matière enseignée par l'école (Mathématiques, SVT…), dans un ou plusieurs cycles. Le {@code
 * groupe} (domaine ou discipline mère) ne sert qu'à l'affichage : au collège, Dissertation et
 * Orthographe-Grammaire restent deux matières distinctes du groupe « Français ».
 *
 * <p>Les coefficients (niveau, série, matière) et le barème du primaire seront dans une table à
 * part : la matière elle-même ne change pas quand ils changent.
 */
@Entity
@Table(name = "matiere")
public class Matiere extends TenantAwareEntity {

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 80)
  private String libelle;

  @Column(length = 60)
  private String groupe;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "varchar(12)[]")
  private String[] cycles;

  @Column(nullable = false)
  private short ordre;

  @Column(nullable = false)
  private boolean actif = true;

  protected Matiere() {}

  public static Matiere creer(
      String code, String libelle, String groupe, Collection<CodeCycle> cycles, int ordre) {
    Matiere m = new Matiere();
    m.renseigner(code, libelle, groupe, cycles, ordre);
    return m;
  }

  public void modifier(
      String code,
      String libelle,
      String groupe,
      Collection<CodeCycle> cycles,
      int ordre,
      boolean actif) {
    renseigner(code, libelle, groupe, cycles, ordre);
    this.actif = actif;
  }

  private void renseigner(
      String code, String libelle, String groupe, Collection<CodeCycle> cycles, int ordre) {
    this.code = Verif.code(code, "Le code");
    this.libelle = Verif.exiger(libelle, "Le libellé", 80);
    this.groupe = Verif.optionnel(groupe, "Le groupe", 60);
    if (cycles == null || cycles.isEmpty()) {
      throw Verif.invalide("CYCLES_REQUIS", "Choisissez au moins un cycle pour cette matière.");
    }
    // Ordre de déclaration des cycles : stable, quel que soit l'ordre reçu.
    this.cycles = EnumSet.copyOf(cycles).stream().map(Enum::name).toArray(String[]::new);
    if (ordre < 0 || ordre > 999) {
      throw Verif.invalide("CHAMP_INVALIDE", "L'ordre d'affichage va de 0 à 999.");
    }
    this.ordre = (short) ordre;
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

  public String getGroupe() {
    return groupe;
  }

  public Set<CodeCycle> getCycles() {
    EnumSet<CodeCycle> resultat = EnumSet.noneOf(CodeCycle.class);
    Arrays.stream(cycles).map(CodeCycle::valueOf).forEach(resultat::add);
    return resultat;
  }

  public int getOrdre() {
    return ordre;
  }

  public boolean isActif() {
    return actif;
  }
}
