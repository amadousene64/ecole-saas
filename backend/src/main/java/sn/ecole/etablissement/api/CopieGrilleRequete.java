package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Cycle dont la grille est recopiée dans le cycle de l'adresse (encore vide). */
public record CopieGrilleRequete(@NotNull UUID depuisCycleId) {}
