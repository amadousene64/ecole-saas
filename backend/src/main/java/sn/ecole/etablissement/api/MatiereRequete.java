package sn.ecole.etablissement.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import sn.ecole.etablissement.CodeCycle;

/** {@code actif} n'est pris en compte qu'à la modification (vrai par défaut). */
public record MatiereRequete(
    @NotBlank @Size(max = 10) String code,
    @NotBlank @Size(max = 80) String libelle,
    @Size(max = 60) String groupe,
    @NotEmpty Set<CodeCycle> cycles,
    @Min(0) @Max(999) int ordre,
    Boolean actif) {}
