package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Une option d'enseignement (LV2…) choisie par l'élève pour son inscription. */
@Entity
@Table(name = "inscription_option")
public class InscriptionOption extends TenantAwareEntity {

  @Column(name = "inscription_id", nullable = false, updatable = false)
  private UUID inscriptionId;

  @Column(name = "option_id", nullable = false, updatable = false)
  private UUID optionId;

  protected InscriptionOption() {}

  public static InscriptionOption choisir(UUID inscriptionId, UUID optionId) {
    InscriptionOption c = new InscriptionOption();
    c.inscriptionId = Objects.requireNonNull(inscriptionId);
    c.optionId = Objects.requireNonNull(optionId);
    return c;
  }

  public void retirer(Instant quand) {
    marquerSupprime(Objects.requireNonNull(quand));
  }

  /**
   * Les options choisies doivent être proposées par la classe, et au plus une par groupe (une seule
   * LV2…).
   *
   * @param groupesProposes options proposées par la classe : identifiant → groupe
   */
  public static void verifierChoix(Collection<UUID> choisies, Map<UUID, String> groupesProposes) {
    Set<String> groupes = new HashSet<>();
    for (UUID option : choisies) {
      String groupe = groupesProposes.get(option);
      if (groupe == null) {
        throw Saisies.regle(
            "OPTION_NON_PROPOSEE", "Cette option n'est pas proposée par la classe.");
      }
      if (!groupes.add(groupe)) {
        throw Saisies.regle(
            "OPTIONS_INCOMPATIBLES", "Une seule option par groupe (" + groupe + ").");
      }
    }
  }

  public UUID getInscriptionId() {
    return inscriptionId;
  }

  public UUID getOptionId() {
    return optionId;
  }
}
