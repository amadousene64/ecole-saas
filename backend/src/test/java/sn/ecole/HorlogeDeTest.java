package sn.ecole;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Horloge de l'application pendant les tests : l'heure système (fuseau de Dakar) par défaut ; un
 * test la fixe ou l'avance au lieu d'attendre ({@code docs/vie-scolaire.md}, « Temps »). Remise à
 * l'heure système avant chaque test ({@link TestIntegration}).
 */
public class HorlogeDeTest extends Clock {

  static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

  private volatile Instant fixe;

  /** Fixe l'horloge à ce jour, à 10 h (heure de Dakar). */
  public void fixer(LocalDate jour) {
    fixe = jour.atTime(LocalTime.of(10, 0)).atZone(DAKAR).toInstant();
  }

  public void avancer(Duration duree) {
    fixe = instant().plus(duree);
  }

  public void reinitialiser() {
    fixe = null;
  }

  @Override
  public ZoneId getZone() {
    return DAKAR;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return Clock.fixed(instant(), zone);
  }

  @Override
  public Instant instant() {
    Instant f = fixe;
    return f != null ? f : Instant.now();
  }

  /** Remplace l'horloge de l'application dans le contexte des tests. */
  @TestConfiguration(proxyBeanMethods = false)
  public static class Configuration {

    @Bean
    @Primary
    HorlogeDeTest horlogeDeTest() {
      return new HorlogeDeTest();
    }
  }
}
