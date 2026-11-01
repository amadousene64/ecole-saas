package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** Liste complète des options proposées par la classe (remplace la précédente). */
public record ClasseOptionsRequete(@NotNull @Size(max = 30) Set<@NotNull UUID> optionIds) {}
