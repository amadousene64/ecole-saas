package sn.ecole.shared.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sn.ecole.shared.domain.OutboxEvent;
import sn.ecole.shared.domain.OutboxEventRepository;

/**
 * Relais de l'outbox : pour chaque école active, traite les événements échus, chacun dans sa propre
 * transaction et sous verrou ({@code SKIP LOCKED}). Un échec est consigné et retenté plus tard,
 * avec un délai qui double à chaque fois (plafonné à une heure) ; au-delà de {@link
 * #TENTATIVES_MAX} l'événement reste en base, non traité, pour examen.
 *
 * <p>Le déclenchement périodique s'active avec {@code ecole.outbox.relais.actif=true} ; les tests
 * appellent {@link #traiterTout()} directement.
 */
@Component
public class RelaisOutbox {

  private static final Logger LOG = LoggerFactory.getLogger(RelaisOutbox.class);

  public static final int TENTATIVES_MAX = 10;
  private static final int LOT = 50;
  private static final Duration DELAI_INITIAL = Duration.ofSeconds(30);
  private static final Duration DELAI_MAX = Duration.ofHours(1);

  private final ExecutionPourTenant execution;
  private final OutboxEventRepository outbox;
  private final Map<String, TraitementOutbox> traitements;
  private final boolean actif;

  public RelaisOutbox(
      ExecutionPourTenant execution,
      OutboxEventRepository outbox,
      List<TraitementOutbox> traitements,
      @Value("${ecole.outbox.relais.actif:false}") boolean actif) {
    this.execution = execution;
    this.outbox = outbox;
    this.traitements =
        traitements.stream()
            .collect(
                Collectors.toUnmodifiableMap(TraitementOutbox::typeEvenement, Function.identity()));
    this.actif = actif;
  }

  @Scheduled(fixedDelayString = "${ecole.outbox.relais.delai:5s}")
  void planifie() {
    if (actif) {
      traiterTout();
    }
  }

  /** Traite les événements échus de toutes les écoles actives ; renvoie le nombre traités. */
  public int traiterTout() {
    int traites = 0;
    for (UUID ecole : execution.ecolesActives()) {
      traites += traiterEcole(ecole);
    }
    return traites;
  }

  /** Traite les événements échus d'une école ; renvoie le nombre traités avec succès. */
  public int traiterEcole(UUID ecole) {
    List<UUID> ids =
        execution.lire(ecole, () -> outbox.aTraiter(Instant.now(), TENTATIVES_MAX, LOT));
    int traites = 0;
    for (UUID id : ids) {
      if (traiterUn(ecole, id)) {
        traites++;
      }
    }
    return traites;
  }

  private boolean traiterUn(UUID ecole, UUID id) {
    try {
      return execution.executer(
          ecole,
          () -> {
            Optional<OutboxEvent> verrouille = outbox.verrouiller(id);
            if (verrouille.isEmpty()) {
              return false; // déjà traité, ou pris par un autre relais
            }
            OutboxEvent evenement = verrouille.get();
            TraitementOutbox traitement = traitements.get(evenement.getTypeEvenement());
            if (traitement == null) {
              throw new IllegalStateException(
                  "Aucun traitement pour " + evenement.getTypeEvenement());
            }
            traitement.traiter(evenement);
            evenement.marquerTraite(Instant.now());
            outbox.save(evenement);
            return true;
          });
    } catch (RuntimeException e) {
      // Le message de l'exception peut citer des données : seul son type est conservé.
      String erreur = e.getClass().getSimpleName();
      LOG.warn("Événement d'outbox {} en échec ({})", id, erreur);
      execution.executer(
          ecole,
          () ->
              outbox
                  .verrouiller(id)
                  .ifPresent(
                      evenement -> {
                        evenement.enregistrerEchec(
                            erreur, Instant.now().plus(delai(evenement.getTentatives())));
                        outbox.save(evenement);
                      }));
      return false;
    }
  }

  static Duration delai(int tentativesPrecedentes) {
    Duration delai = DELAI_INITIAL.multipliedBy(1L << Math.min(tentativesPrecedentes, 10));
    return delai.compareTo(DELAI_MAX) > 0 ? DELAI_MAX : delai;
  }
}
