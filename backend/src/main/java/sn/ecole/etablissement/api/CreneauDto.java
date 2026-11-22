package sn.ecole.etablissement.api;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import sn.ecole.etablissement.domain.TypeCreneau;

/** Créneau de la grille horaire d'un cycle. */
public record CreneauDto(
    UUID id,
    Integer version,
    UUID cycleId,
    LocalTime debut,
    LocalTime fin,
    Set<DayOfWeek> jours,
    TypeCreneau type,
    String libelle) {}
