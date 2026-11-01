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
 * @param clientsActivation clients dont les jetons peuvent activer un code d'école (rattachement
 *     d'un parent ou d'un élève) ; par défaut l'espace parent seul. Un compte créé par
 *     auto-inscription via un autre client ne peut donc pas se rattacher à une école.
 */
@ConfigurationProperties("ecole.securite.jeton")
public record JetonProperties(List<String> clientsAutorises, List<String> clientsActivation) {

  public JetonProperties {
    if (clientsAutorises == null || clientsAutorises.isEmpty()) {
      throw new IllegalStateException("ecole.securite.jeton.clients-autorises est obligatoire");
    }
    clientsAutorises = List.copyOf(clientsAutorises);
    clientsActivation =
        clientsActivation == null || clientsActivation.isEmpty()
            ? List.of("parent")
            : List.copyOf(clientsActivation);
  }

  Set<String> ensemble() {
    return Set.copyOf(clientsAutorises);
  }
}
