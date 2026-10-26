package sn.ecole.shared.api;

import java.util.List;
import java.util.UUID;

/**
 * Utilisateur connecté dans l'école courante.
 *
 * @param roles rôles dans CETTE école, triés.
 */
public record MoiDto(UUID utilisateurId, UUID ecoleId, List<String> roles) {}
