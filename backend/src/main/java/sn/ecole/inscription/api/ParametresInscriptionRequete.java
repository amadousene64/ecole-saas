package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.ecole.inscription.domain.PolitiqueCapacite;

/** Format : texte fixe et jetons {AAAA}, {AA}, {SEQ:n} (ex. « LYA-{AA}-{SEQ:4} »). */
public record ParametresInscriptionRequete(
    @NotBlank @Size(max = 60) String formatMatricule,
    @NotNull PolitiqueCapacite politiqueCapacite) {}
