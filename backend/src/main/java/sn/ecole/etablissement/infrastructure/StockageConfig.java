package sn.ecole.etablissement.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StockageProperties.class)
class StockageConfig {}
