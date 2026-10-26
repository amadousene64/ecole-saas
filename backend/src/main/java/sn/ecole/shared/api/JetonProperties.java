package sn.ecole.shared.api;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Contrôles propres à la plateforme sur les jetons Keycloak, en plus de la signature, de l'émetteur
 * et de la date.
 *
 * @param clientsAutorises clients Keycloak (revendication {@code azp}) dont les jetons sont
 *     acceptés. Obligatoire et non vide : un jeton d'un autre client du realm est refusé.
 */
@ConfigurationProperties("ecole.securite.jeton")
public record JetonProperties(List<String> clientsAutorises) {

  public JetonProperties {
    if (clientsAutorises == null || clientsAutorises.isEmpty()) {
      throw new IllegalStateException("ecole.securite.jeton.clients-autorises est obligatoire");
    }
    clientsAutorises = List.copyOf(clientsAutorises);
  }

  Set<String> ensemble() {
    return Set.copyOf(clientsAutorises);
  }
}
