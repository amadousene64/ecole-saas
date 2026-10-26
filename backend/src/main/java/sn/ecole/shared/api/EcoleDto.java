package sn.ecole.shared.api;

import java.util.UUID;

/** École où l'utilisateur connecté a un rôle. */
public record EcoleDto(UUID id, String code, String nom) {}
