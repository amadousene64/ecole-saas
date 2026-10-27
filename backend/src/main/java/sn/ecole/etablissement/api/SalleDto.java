package sn.ecole.etablissement.api;

import java.util.UUID;

public record SalleDto(UUID id, Integer version, String nom, int capacite, boolean actif) {}
