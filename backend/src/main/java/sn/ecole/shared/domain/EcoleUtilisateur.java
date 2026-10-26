package sn.ecole.shared.domain;

import java.util.UUID;

/** École dans laquelle un utilisateur a au moins un rôle actif. */
public record EcoleUtilisateur(UUID id, String code, String nom) {}
