package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Libellé AAAA-AAAA+1 (ex. 2026-2027) ; les années des dates doivent y correspondre. */
public record AnneeScolaireRequete(
    @NotBlank @Size(max = 9) String libelle,
    @NotNull LocalDate dateDebut,
    @NotNull LocalDate dateFin) {}
