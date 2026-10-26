/**
 * Socle partagé : multi-tenant (contexte, RLS, filtre Hibernate), appartenances et rôles par école,
 * audit, outbox, erreurs ProblemDetail. Module ouvert : les autres modules peuvent en utiliser tous
 * les types.
 */
@ApplicationModule(displayName = "Socle partagé", type = ApplicationModule.Type.OPEN)
package sn.ecole.shared;

import org.springframework.modulith.ApplicationModule;
