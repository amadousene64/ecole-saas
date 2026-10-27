package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ModifierPeriodeRequete(
    @NotBlank @Size(max = 40) String libelle,
    @NotNull LocalDate dateDebut,
    @NotNull LocalDate dateFin) {}
