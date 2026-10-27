package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import sn.ecole.etablissement.domain.TypeEvenement;

/** Un jour férié a la même date de début et de fin. */
public record EvenementRequete(
    @NotNull TypeEvenement type,
    @NotBlank @Size(max = 120) String libelle,
    @NotNull LocalDate dateDebut,
    @NotNull LocalDate dateFin) {}
