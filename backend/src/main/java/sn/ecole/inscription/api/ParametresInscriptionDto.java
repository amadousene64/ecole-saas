package sn.ecole.inscription.api;

import sn.ecole.inscription.domain.PolitiqueCapacite;

/** {@code exempleMatricule} : à quoi ressemblera le prochain matricule de l'année. */
public record ParametresInscriptionDto(
    String formatMatricule, PolitiqueCapacite politiqueCapacite, String exempleMatricule) {}
