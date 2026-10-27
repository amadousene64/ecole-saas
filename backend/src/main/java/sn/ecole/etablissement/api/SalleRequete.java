package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code actif} n'est pris en compte qu'à la modification (vrai par défaut). */
public record SalleRequete(
    @NotBlank @Size(max = 60) String nom, @NotNull Integer capacite, Boolean actif) {}
