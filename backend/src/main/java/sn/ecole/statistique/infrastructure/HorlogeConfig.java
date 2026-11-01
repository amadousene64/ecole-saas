package sn.ecole.statistique.infrastructure;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge injectable (les tests la figent). Les dates de l'école suivent le fuseau de Dakar. */
@Configuration(proxyBeanMethods = false)
class HorlogeConfig {

  @Bean
  Clock horloge() {
    return Clock.system(ZoneId.of("Africa/Dakar"));
  }
}
