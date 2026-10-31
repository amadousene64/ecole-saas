package sn.ecole.inscription.api;

import java.time.Instant;
import java.util.UUID;
import sn.ecole.inscription.domain.PieceDossier;

/** Pièce du dossier ; téléchargeable seulement si {@code statut} vaut SAIN. */
public record PieceDto(
    UUID id,
    PieceDossier.Type type,
    String contentType,
    int taille,
    PieceDossier.Statut statut,
    Instant creeLe,
    Instant analyseLe) {}
