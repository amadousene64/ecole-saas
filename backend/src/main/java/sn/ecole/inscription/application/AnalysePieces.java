package sn.ecole.inscription.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sn.ecole.inscription.domain.AntiVirus;
import sn.ecole.inscription.domain.PieceDossier;
import sn.ecole.inscription.domain.PieceDossierRepository;
import sn.ecole.shared.application.ApresTransaction;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.application.TraitementOutbox;
import sn.ecole.shared.domain.OutboxEvent;
import sn.ecole.shared.domain.StockageFichiers;

/**
 * Analyse antivirus d'une pièce déposée, déclenchée par le relais de l'outbox (tâche de fond, dans
 * l'école de la pièce). Sain : la pièce devient accessible. Infecté : elle est retirée du stockage
 * et reste inaccessible. ClamAV injoignable : exception, le relais retentera plus tard.
 *
 * <p>Idempotent : une pièce déjà analysée ou supprimée est ignorée.
 */
@Component
class AnalysePieces implements TraitementOutbox {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysePieces.class);

  private final PieceDossierRepository pieces;
  private final StockageFichiers stockage;
  private final AntiVirus antivirus;
  private final JournalAudit journal;

  AnalysePieces(
      PieceDossierRepository pieces,
      StockageFichiers stockage,
      AntiVirus antivirus,
      JournalAudit journal) {
    this.pieces = pieces;
    this.stockage = stockage;
    this.antivirus = antivirus;
    this.journal = journal;
  }

  @Override
  public String typeEvenement() {
    return PieceService.ANALYSE_DEMANDEE;
  }

  @Override
  public void traiter(OutboxEvent evenement) {
    UUID pieceId = evenement.getIdAgregat();
    PieceDossier piece = pieces.findById(pieceId).orElse(null);
    if (piece == null || piece.getStatut() != PieceDossier.Statut.EN_ANALYSE) {
      return;
    }
    AntiVirus.Resultat resultat = antivirus.analyser(stockage.lire(piece.getCle()));
    piece.enregistrerAnalyse(resultat.sain(), Instant.now());
    pieces.save(piece);
    if (resultat.sain()) {
      journal.consignerSysteme(
          "PIECE_ANALYSEE", "Eleve", piece.getEleveId(), Map.of("piece", pieceId, "sain", true));
      return;
    }
    String cle = piece.getCle();
    ApresTransaction.siValidee(() -> stockage.supprimer(cle));
    journal.consignerSysteme(
        "PIECE_INFECTEE",
        "Eleve",
        piece.getEleveId(),
        Map.of("piece", pieceId, "signature", resultat.signature()));
    LOG.warn("Pièce {} bloquée par l'antivirus ({})", pieceId, resultat.signature());
  }
}
