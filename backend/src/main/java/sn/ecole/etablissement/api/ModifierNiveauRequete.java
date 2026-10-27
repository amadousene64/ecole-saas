package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Changer {@code baseMoyenne} (10, 20 ou null pour suivre le cycle) est réservé au directeur. */
public record ModifierNiveauRequete(
    @NotBlank @Size(max = 80) String libelle,
    @NotNull Integer ordre,
    Integer baseMoyenne,
    @NotNull Boolean actif) {}
