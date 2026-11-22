package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import sn.ecole.etablissement.domain.TypeCreneau;

/** Heures au format HH:MM ; {@code type} : COURS par défaut. */
public record CreneauRequete(
    @NotNull LocalTime debut,
    @NotNull LocalTime fin,
    @NotEmpty Set<@NotNull DayOfWeek> jours,
    TypeCreneau type,
    @Size(max = 30) String libelle) {}
