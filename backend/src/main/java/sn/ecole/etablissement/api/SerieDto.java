package sn.ecole.etablissement.api;

import java.util.UUID;

public record SerieDto(UUID id, Integer version, String code, String libelle, boolean actif) {}
