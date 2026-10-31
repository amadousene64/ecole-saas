package sn.ecole.inscription.application;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.ControlePiece;
import sn.ecole.inscription.domain.EleveRepository;
import sn.ecole.inscription.domain.PieceDossier;
import sn.ecole.inscription.domain.PieceDossierRepository;
import sn.ecole.shared.application.ApresTransaction;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.application.OutboxPublisher;
import sn.ecole.shared.domain.ContexteTenant;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.Identifiants;
import sn.ecole.shared.domain.RoleEcole;
import sn.ecole.shared.domain.StockageFichiers;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Pièces du dossier (acte de naissance, certificats, photo d'identité) : direction et secrétariat.
 *
 * <p>Le certificat médical est une donnée de santé et suit les règles des données sensibles : la
 * direction et le secrétariat le déposent, seuls le DIRECTEUR et le CENSEUR l'ouvrent, et chaque
 * ouverture est journalisée ({@link DossierSensibleService#LECTURE}). Le secrétariat le voit dans
 * la liste (il sait qu'il est déposé) sans pouvoir l'ouvrir ; le censeur ne voit que lui.
 *
 * <p>Dépôt : type vérifié par les octets magiques, taille bornée, fichier stocké sous une clé
 * construite par le serveur (image ré-encodée, sans métadonnées), pièce EN_ANALYSE et demande
 * d'analyse écrite dans l'outbox (même transaction). Le relais la confie à ClamAV ({@link
 * AnalysePieces}). Tant que la pièce n'est pas SAIN, aucune URL n'est délivrée ; une fois saine,
 * l'URL signée vaut 5 minutes et force le téléchargement.
 */
@Service
@Transactional
public class PieceService implements UtilisationEleve {

  public static final String ANALYSE_DEMANDEE = "PIECE_A_ANALYSER";
  public static final Duration VALIDITE_URL = Duration.ofMinutes(5);

  private final PieceDossierRepository pieces;
  private final EleveRepository eleves;
  private final StockageFichiers stockage;
  private final OutboxPublisher outbox;
  private final JournalAudit journal;

  public PieceService(
      PieceDossierRepository pieces,
      EleveRepository eleves,
      StockageFichiers stockage,
      OutboxPublisher outbox,
      JournalAudit journal) {
    this.pieces = pieces;
    this.eleves = eleves;
    this.stockage = stockage;
    this.outbox = outbox;
    this.journal = journal;
  }

  @PreAuthorize(Droits.ECRITURE)
  public PieceDossier deposer(UUID eleveId, PieceDossier.Type type, byte[] contenu) {
    eleves.findById(eleveId).orElseThrow(Erreurs::eleveIntrouvable);
    if (type == null) {
      throw Erreurs.invalide("CHAMP_INVALIDE", "Le type de pièce est obligatoire.");
    }
    ControlePiece.PieceValidee validee = ControlePiece.controler(contenu);
    String cle =
        ContexteTenant.exiger() + "/eleves/" + eleveId + "/pieces/" + Identifiants.uuidV7();
    stockage.deposer(cle, validee.octets(), validee.contentType());
    ApresTransaction.siAnnulee(() -> stockage.supprimer(cle));
    PieceDossier piece = pieces.save(PieceDossier.deposer(eleveId, type, cle, validee));
    outbox.publier("PieceDossier", piece.getId(), ANALYSE_DEMANDEE, Map.of("piece", piece.getId()));
    journal.consigner(
        "PIECE_DEPOSEE",
        "Eleve",
        eleveId,
        Map.of("piece", piece.getId(), "type", type, "sha256", validee.sha256()));
    return piece;
  }

  @PreAuthorize(Droits.PIECES_LECTURE)
  @Transactional(readOnly = true)
  public List<PieceDossier> lister(UUID eleveId) {
    eleves.findById(eleveId).orElseThrow(Erreurs::eleveIntrouvable);
    List<PieceDossier> toutes = pieces.findByEleveIdOrderByCreeLeDesc(eleveId);
    if (ecriture()) {
      return toutes;
    }
    // Censeur : le certificat médical seulement.
    return toutes.stream().filter(PieceDossier::estSensible).toList();
  }

  /**
   * URL de téléchargement (5 minutes), seulement pour une pièce analysée et saine. Certificat
   * médical : DIRECTEUR et CENSEUR seulement, ouverture journalisée comme une lecture de données
   * sensibles ; autres pièces : direction et secrétariat.
   */
  @PreAuthorize(Droits.PIECES_LECTURE)
  public URI url(UUID eleveId, UUID pieceId) {
    PieceDossier piece = charger(eleveId, pieceId);
    boolean autorise =
        piece.estSensible()
            ? Erreurs.aLeRole(RoleEcole.DIRECTEUR) || Erreurs.aLeRole(RoleEcole.CENSEUR)
            : ecriture();
    if (!autorise) {
      throw new ErreurMetier(
          "ACCES_REFUSE", TypeErreur.INTERDIT, "Vous n'avez pas accès à cette pièce.");
    }
    if (!piece.disponible()) {
      throw Erreurs.conflit(
          "PIECE_NON_DISPONIBLE",
          piece.getStatut() == PieceDossier.Statut.INFECTE
              ? "Cette pièce a été bloquée par l'antivirus."
              : "Cette pièce est en cours d'analyse antivirus : réessayez dans un instant.");
    }
    if (piece.estSensible()) {
      journal.consigner(
          DossierSensibleService.LECTURE,
          "Eleve",
          eleveId,
          Map.of("champs", List.of("certificat_medical"), "piece", pieceId));
    } else {
      journal.consigner("PIECE_CONSULTEE", "Eleve", eleveId, Map.of("piece", pieceId));
    }
    return stockage.urlTelechargement(
        piece.getCle(), VALIDITE_URL, piece.getContentType(), piece.nomFichier());
  }

  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID eleveId, UUID pieceId) {
    PieceDossier piece = charger(eleveId, pieceId);
    piece.supprimer(Instant.now());
    pieces.save(piece);
    String cle = piece.getCle();
    ApresTransaction.siValidee(() -> stockage.supprimer(cle));
    journal.consigner("PIECE_SUPPRIMEE", "Eleve", eleveId, Map.of("piece", pieceId));
  }

  @Override
  public boolean eleveUtilise(UUID eleveId) {
    return pieces.existsByEleveId(eleveId);
  }

  private static boolean ecriture() {
    return Erreurs.aLeRole(RoleEcole.DIRECTEUR) || Erreurs.aLeRole(RoleEcole.SECRETARIAT);
  }

  private PieceDossier charger(UUID eleveId, UUID pieceId) {
    return pieces
        .findById(pieceId)
        .filter(p -> p.getEleveId().equals(eleveId))
        .orElseThrow(() -> Erreurs.introuvable("PIECE_INTROUVABLE", "Pièce introuvable."));
  }
}
