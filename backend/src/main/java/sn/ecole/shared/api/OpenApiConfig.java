package sn.ecole.shared.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info = @Info(title = "API Gestion scolaire", version = "v1"),
    security = @SecurityRequirement(name = "jwt"))
@SecurityScheme(
    name = "jwt",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
class OpenApiConfig {

  /** Documente l'en-tête X-Tenant-Id (dev) sur chaque opération de l'API. */
  @Bean
  GlobalOpenApiCustomizer enteteTenant() {
    return api -> {
      if (api.getPaths() == null) {
        return;
      }
      api.getPaths()
          .forEach(
              (chemin, operations) -> {
                if (chemin.startsWith("/api/")
                    && !chemin.equals(SecuriteConfig.CHEMIN_MES_ECOLES)) {
                  operations
                      .readOperations()
                      .forEach(
                          op ->
                              op.addParametersItem(
                                  new HeaderParameter()
                                      .name(ResolveurTenantRequete.ENTETE)
                                      .description(
                                          "École demandée (dev). En production : sous-domaine.")
                                      .required(false)
                                      .schema(new StringSchema().format("uuid"))));
                }
              });
    };
  }
}
