package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code baseMoyenne} (10 ou 20) surcharge celle du cycle : réservé au directeur. */
public record CreerNiveauRequete(
    @NotNull UUID cycleId,
    @NotBlank @Size(max = 10) String code,
    @NotBlank @Size(max = 80) String libelle,
    @NotNull Integer ordre,
    Integer baseMoyenne) {}
