package sn.ecole.inscription.api;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Identifiants d'opération OpenAPI stables pour ce module : {@code <ressource>_<méthode>}, par
 * exemple {@code cycle_lister}. Sans cela, springdoc numérote les doublons ({@code lister_6}) et
 * les noms du client Angular généré changent dès qu'on ajoute un contrôleur.
 */
@Configuration(proxyBeanMethods = false)
class IdentifiantsOperationsInscription {

  @Bean
  OperationCustomizer identifiantsStablesInscription() {
    return (operation, methode) -> {
      if (methode.getBeanType().getPackageName().startsWith("sn.ecole.inscription.api")) {
        String ressource = methode.getBeanType().getSimpleName().replaceFirst("Controller$", "");
        operation.setOperationId(
            Character.toLowerCase(ressource.charAt(0))
                + ressource.substring(1)
                + "_"
                + methode.getMethod().getName());
      }
      return operation;
    };
  }
}
