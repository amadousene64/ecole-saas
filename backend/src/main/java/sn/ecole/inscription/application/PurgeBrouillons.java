package sn.ecole.inscription.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sn.ecole.inscription.domain.BrouillonInscription;
import sn.ecole.inscription.domain.BrouillonInscriptionRepository;
import sn.ecole.shared.application.BilanExecution;
import sn.ecole.shared.application.ExecutionPourTenant;
import sn.ecole.shared.application.JournalAudit;

/**
 * Tâche quotidienne : supprime, école par école (inactives comprises), les brouillons d'inscription
 * non terminés et non modifiés depuis {@link BrouillonInscription#CONSERVATION}. Comme un abandon :
 * contenu chiffré effacé, brouillon clos, pièces marquées supprimées et leurs fichiers retirés du
 * stockage APRÈS validation de la transaction de l'école.
 *
 * <p>Le journal d'audit reçoit, par école, le seul nombre de brouillons supprimés (aucun nom, aucun
 * identifiant d'élève). Désactivable par {@code ecole.brouillons.purge.actif=false} ; les tests
 * appellent {@link #purger(Instant)} directement.
 */
@Component
public class PurgeBrouillons {

  private static final Logger LOG = LoggerFactory.getLogger(PurgeBrouillons.class);

  static final String TACHE = "purge des brouillons d'inscription";

  private final ExecutionPourTenant execution;
  private final BrouillonInscriptionRepository brouillons;
  private final PieceService pieces;
  private final JournalAudit journal;
  private final boolean actif;

  public PurgeBrouillons(
      ExecutionPourTenant execution,
      BrouillonInscriptionRepository brouillons,
      PieceService pieces,
      JournalAudit journal,
      @Value("${ecole.brouillons.purge.actif:true}") boolean actif) {
    this.execution = execution;
    this.brouillons = brouillons;
    this.pieces = pieces;
    this.journal = journal;
    this.actif = actif;
  }

  /** Chaque nuit à 3 h 30, heure de Dakar. */
  @Scheduled(cron = "${ecole.brouillons.purge.cron:0 30 3 * * *}", zone = "Africa/Dakar")
  void planifie() {
    if (actif) {
      purger(Instant.now());
    }
  }

  /** Supprime les brouillons expirés à {@code maintenant}, école par école. */
  public BilanExecution purger(Instant maintenant) {
    Instant limite = maintenant.minus(BrouillonInscription.CONSERVATION);
    // Écoles inactives comprises : la durée de conservation vaut aussi pour une école suspendue.
    BilanExecution bilan =
        execution.pourToutesLesEcoles(TACHE, ecole -> purgerEcole(ecole, limite));
    if (!bilan.toutReussi()) {
      LOG.warn("Purge des brouillons : {} école(s) en échec", bilan.echouees().size());
    }
    return bilan;
  }

  private void purgerEcole(UUID ecole, Instant limite) {
    int supprimes = 0;
    for (UUID id : brouillons.expiresAvant(limite)) {
      // Chargé à jour sous verrou, puis nouvelle vérification : un brouillon repris entre-temps
      // (ou finalisé, abandonné) est laissé tel quel.
      BrouillonInscription b = brouillons.verrouiller(id).orElse(null);
      if (b == null || !b.getModifieLe().isBefore(limite)) {
        continue;
      }
      pieces.abandonnerDuBrouillon(b.getId());
      b.clore(Instant.now());
      brouillons.saveAndFlush(b);
      supprimes++;
    }
    if (supprimes > 0) {
      journal.consignerSysteme(
          "BROUILLONS_EXPIRES",
          "Tenant",
          ecole,
          Map.of(
              "nombre",
              supprimes,
              "conservationJours",
              BrouillonInscription.CONSERVATION.toDays()));
      LOG.info("Purge des brouillons : {} supprimé(s) pour l'école {}", supprimes, ecole);
    }
  }
}
