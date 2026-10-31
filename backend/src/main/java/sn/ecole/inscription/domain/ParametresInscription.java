package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Réglages d'inscription de l'école : format des matricules, politique de capacité. */
@Entity
@Table(name = "parametres_inscription")
public class ParametresInscription extends TenantAwareEntity {

  @Column(name = "format_matricule", nullable = false, length = 60)
  private String formatMatricule;

  @Enumerated(EnumType.STRING)
  @Column(name = "politique_capacite", nullable = false, length = 10)
  private PolitiqueCapacite politiqueCapacite;

  protected ParametresInscription() {}

  /** Valeurs par défaut : matricule {@link FormatMatricule#PAR_DEFAUT}, capacité AVERTIR. */
  public static ParametresInscription parDefaut() {
    ParametresInscription p = new ParametresInscription();
    p.formatMatricule = FormatMatricule.PAR_DEFAUT;
    p.politiqueCapacite = PolitiqueCapacite.AVERTIR;
    return p;
  }

  public void modifier(String formatMatricule, PolitiqueCapacite politique) {
    this.formatMatricule = FormatMatricule.lire(formatMatricule).format();
    this.politiqueCapacite = Objects.requireNonNull(politique, "politique");
  }

  public FormatMatricule format() {
    return FormatMatricule.lire(formatMatricule);
  }

  public String getFormatMatricule() {
    return formatMatricule;
  }

  public PolitiqueCapacite getPolitiqueCapacite() {
    return politiqueCapacite;
  }
}
