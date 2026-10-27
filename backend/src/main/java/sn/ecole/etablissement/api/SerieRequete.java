package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code actif} n'est pris en compte qu'à la modification (vrai par défaut). */
public record SerieRequete(
    @NotBlank @Size(max = 10) String code,
    @NotBlank @Size(max = 80) String libelle,
    Boolean actif) {}
