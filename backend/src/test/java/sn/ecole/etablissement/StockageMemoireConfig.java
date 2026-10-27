package sn.ecole.etablissement;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class StockageMemoireConfig {

  @Bean
  @Primary
  StockageMemoire stockageMemoire() {
    return new StockageMemoire();
  }
}
