package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code actif} n'est pris en compte qu'à la modification (vrai par défaut). */
public record OptionEnseignementRequete(
    @NotBlank @Size(max = 10) String code,
    @NotBlank @Size(max = 80) String libelle,
    @NotBlank @Size(max = 20) String groupe,
    Boolean actif) {}
