package sn.ecole.etablissement.api;

import java.time.LocalDate;
import java.util.UUID;
import sn.ecole.etablissement.domain.TypeEvenement;

public record EvenementDto(
    UUID id,
    Integer version,
    UUID anneeId,
    TypeEvenement type,
    String libelle,
    LocalDate dateDebut,
    LocalDate dateFin) {}
