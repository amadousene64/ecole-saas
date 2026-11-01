package sn.ecole.statistique.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.statistique.domain.PeriodeCourbe;
import sn.ecole.statistique.domain.TableauDeBord;
import sn.ecole.statistique.domain.TableauDeBord.EffectifCycle;
import sn.ecole.statistique.domain.TableauDeBord.EffectifNiveau;
import sn.ecole.statistique.domain.TableauDeBord.EffectifStatut;
import sn.ecole.statistique.domain.TableauDeBord.Part;
import sn.ecole.statistique.domain.TableauDeBord.Semaine;

/** Clé par école, aller-retour JSON et tolérance aux pannes du cache. */
class CacheTableauDeBordRedisTest {

  private static final UUID ECOLE_A = UUID.fromString("01930000-0000-7000-8000-0000000000a1");
  private static final UUID ECOLE_B = UUID.fromString("01930000-0000-7000-8000-0000000000b2");
  private static final UUID ANNEE = UUID.fromString("01930000-0000-7000-8000-00000000aa01");

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> valeurs = mock(ValueOperations.class);

  private final CacheTableauDeBordRedis cache =
      new CacheTableauDeBordRedis(redis, Duration.ofMinutes(5));

  private static TableauDeBord exemple() {
    return new TableauDeBord(
        ANNEE,
        "2026-2027",
        Instant.parse("2026-11-04T10:00:00Z"),
        PeriodeCourbe.JOURS_30,
        CodeCycle.MOYEN,
        30,
        List.of(new EffectifCycle(CodeCycle.MOYEN, "Collège", 30)),
        List.of(
            new EffectifNiveau(
                UUID.fromString("01930000-0000-7000-8000-00000000c002"),
                "6E",
                "Sixième",
                CodeCycle.MOYEN,
                30)),
        List.of(new EffectifStatut("INSCRIT", 30)),
        Part.de(30, 20),
        Part.de(25, 5),
        List.of(new Semaine(LocalDate.of(2026, 11, 2), 4)));
  }

  @Test
  void laCleContientToujoursLEcoleEtLesFiltres() {
    String a = CacheTableauDeBordRedis.cle(ECOLE_A, ANNEE, PeriodeCourbe.ANNEE, null);
    String b = CacheTableauDeBordRedis.cle(ECOLE_B, ANNEE, PeriodeCourbe.ANNEE, null);

    assertThat(a).contains(ECOLE_A.toString()).doesNotContain(ECOLE_B.toString()).isNotEqualTo(b);
    assertThat(a).endsWith(":ANNEE:TOUS");
    assertThat(CacheTableauDeBordRedis.cle(ECOLE_A, ANNEE, PeriodeCourbe.JOURS_90, CodeCycle.MOYEN))
        .endsWith(":JOURS_90:MOYEN");
  }

  @Test
  void leTableauFaitLAllerRetourEnJsonAvecUneExpirationCourte() {
    when(redis.opsForValue()).thenReturn(valeurs);
    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

    cache.ecrire(ECOLE_A, ANNEE, PeriodeCourbe.JOURS_30, CodeCycle.MOYEN, exemple());

    verify(valeurs).set(anyString(), json.capture(), eq(Duration.ofMinutes(5)));
    // Que des nombres et des libellés de structure : rien qui désigne un élève.
    assertThat(json.getValue()).doesNotContain("\"nom\"", "prenom", "eleveId");
    when(valeurs.get(anyString())).thenReturn(json.getValue());
    assertThat(cache.lire(ECOLE_A, ANNEE, PeriodeCourbe.JOURS_30, CodeCycle.MOYEN))
        .contains(exemple());
  }

  @Test
  void uneValeurIllisibleOuUnePanneRedisEquivautAUnCacheVide() {
    when(redis.opsForValue()).thenReturn(valeurs);
    when(valeurs.get(anyString())).thenReturn("{pas du json");
    assertThat(cache.lire(ECOLE_A, ANNEE, PeriodeCourbe.ANNEE, null)).isEmpty();

    when(valeurs.get(anyString())).thenThrow(new RedisConnectionFailureException("coupé"));
    assertThat(cache.lire(ECOLE_A, ANNEE, PeriodeCourbe.ANNEE, null)).isEmpty();

    // L'écriture ne casse jamais la réponse du directeur.
    when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("coupé"));
    cache.ecrire(ECOLE_A, ANNEE, PeriodeCourbe.ANNEE, null, exemple());
  }
}
