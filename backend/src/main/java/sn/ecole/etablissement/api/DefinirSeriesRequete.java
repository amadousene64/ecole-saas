package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/** Remplace les séries du niveau. Vide : le niveau n'a pas de série. */
public record DefinirSeriesRequete(@NotNull Set<UUID> serieIds) {}
