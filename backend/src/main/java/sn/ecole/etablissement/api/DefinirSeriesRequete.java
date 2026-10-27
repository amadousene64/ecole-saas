package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** Remplace les séries du niveau. Vide : le niveau n'a pas de série. */
public record DefinirSeriesRequete(@NotNull @Size(max = 20) Set<@NotNull UUID> serieIds) {}
