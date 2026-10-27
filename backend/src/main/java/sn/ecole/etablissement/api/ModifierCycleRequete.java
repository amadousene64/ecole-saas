package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.ecole.etablissement.ModeNotation;
import sn.ecole.etablissement.TypePeriode;

public record ModifierCycleRequete(
    @NotBlank @Size(max = 80) String libelle,
    @NotNull TypePeriode typePeriode,
    @NotNull ModeNotation modeNotation,
    @NotNull Integer baseMoyenne) {}
