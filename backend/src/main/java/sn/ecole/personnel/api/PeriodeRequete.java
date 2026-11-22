package sn.ecole.personnel.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Nouvelle période d'une affectation ; {@code fin} facultative. */
public record PeriodeRequete(@NotNull LocalDate debut, LocalDate fin) {}
