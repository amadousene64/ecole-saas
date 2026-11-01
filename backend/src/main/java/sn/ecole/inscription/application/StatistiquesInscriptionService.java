package sn.ecole.inscription.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.StatistiquesInscription;
import sn.ecole.inscription.domain.StatistiquesInscriptionRepository;
import sn.ecole.inscription.domain.StatistiquesInscriptionRepository.DateEtNombre;

/**
 * Agrégats d'inscription du tableau de bord. L'école courante est appliquée par le filtre Hibernate
 * et la RLS ; seul le directeur y accède.
 */
@Service
@Transactional(readOnly = true)
public class StatistiquesInscriptionService implements StatistiquesInscription {

  /** Au-delà, la plage est refusée : une année scolaire fait moins de deux ans. */
  private static final int SEMAINES_MAX = 120;

  private final StatistiquesInscriptionRepository depot;

  public StatistiquesInscriptionService(StatistiquesInscriptionRepository depot) {
    this.depot = depot;
  }

  @Override
  @PreAuthorize(Droits.DIRECTION)
  public List<EffectifClasse> effectifs(UUID anneeId) {
    return depot.effectifs(anneeId);
  }

  @Override
  @PreAuthorize(Droits.DIRECTION)
  public SuiviClasses suivi(UUID anneeId, Collection<UUID> classeIds) {
    if (classeIds.isEmpty()) {
      return new SuiviClasses(0, 0, 0, 0);
    }
    return depot.suivi(anneeId, classeIds);
  }

  @Override
  @PreAuthorize(Droits.DIRECTION)
  public List<InscriptionsSemaine> inscriptionsParSemaine(
      UUID anneeId, Collection<UUID> classeIds, LocalDate debut, LocalDate fin) {
    if (debut.isAfter(fin)) {
      throw Erreurs.invalide("PLAGE_INVALIDE", "La date de début suit la date de fin.");
    }
    LocalDate premier = debut.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate dernier = fin.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    if (premier.plusWeeks(SEMAINES_MAX).isBefore(dernier)) {
      throw Erreurs.invalide("PLAGE_INVALIDE", "La plage dépasse " + SEMAINES_MAX + " semaines.");
    }
    Map<LocalDate, Long> parSemaine = new TreeMap<>();
    for (LocalDate lundi = premier; !lundi.isAfter(dernier); lundi = lundi.plusWeeks(1)) {
      parSemaine.put(lundi, 0L);
    }
    if (!classeIds.isEmpty()) {
      for (DateEtNombre ligne : depot.entreesParJour(anneeId, classeIds, debut, fin)) {
        LocalDate lundi = ligne.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        parSemaine.merge(lundi, ligne.nombre(), Long::sum);
      }
    }
    List<InscriptionsSemaine> resultat = new ArrayList<>();
    parSemaine.forEach((lundi, n) -> resultat.add(new InscriptionsSemaine(lundi, n)));
    return resultat;
  }
}
