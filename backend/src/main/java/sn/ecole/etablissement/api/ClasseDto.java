package sn.ecole.etablissement.api;

import java.util.UUID;

public record ClasseDto(
    UUID id,
    Integer version,
    UUID anneeId,
    UUID niveauId,
    UUID serieId,
    String nom,
    int capacite,
    UUID salleId) {}
