package sn.ecole.shared.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tâches planifiées (relais de l'outbox…). Chacune décide elle-même si elle est active. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class PlanificationConfig {}
