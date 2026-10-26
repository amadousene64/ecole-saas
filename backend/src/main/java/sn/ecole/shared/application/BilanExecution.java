package sn.ecole.shared.application;

import java.util.List;
import java.util.UUID;

/** Résultat d'une tâche exécutée école par école : aucun échec n'est passé sous silence. */
public record BilanExecution(String tache, List<UUID> reussies, List<UUID> echouees) {

  public BilanExecution {
    reussies = List.copyOf(reussies);
    echouees = List.copyOf(echouees);
  }

  public boolean toutReussi() {
    return echouees.isEmpty();
  }
}
