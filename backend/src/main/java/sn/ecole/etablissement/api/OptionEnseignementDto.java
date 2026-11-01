package sn.ecole.etablissement.api;

import java.util.UUID;

public record OptionEnseignementDto(
    UUID id, Integer version, String code, String libelle, String groupe, boolean actif) {}
