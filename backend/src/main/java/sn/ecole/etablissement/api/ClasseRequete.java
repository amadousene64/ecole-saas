package sn.ecole.etablissement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * La série est obligatoire si le niveau en propose, interdite sinon. {@code anneeId} n'est lu qu'à
 * la création : une classe ne change jamais d'année.
 */
public record ClasseRequete(
    UUID anneeId,
    @NotNull UUID niveauId,
    UUID serieId,
    @NotBlank @Size(max = 30) String nom,
    @NotNull Integer capacite,
    UUID salleId) {}
