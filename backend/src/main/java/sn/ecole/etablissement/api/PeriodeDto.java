package sn.ecole.etablissement.api;

import java.time.LocalDate;
import java.util.UUID;

public record PeriodeDto(
    UUID id,
    Integer version,
    UUID anneeId,
    UUID cycleId,
    int numero,
    String libelle,
    LocalDate dateDebut,
    LocalDate dateFin) {}
