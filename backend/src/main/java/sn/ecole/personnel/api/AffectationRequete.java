package sn.ecole.personnel.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.personnel.domain.TypeAffectation;

/**
 * ENSEIGNEMENT : {@code classeId} et {@code matiereId} ; TITULAIRE : {@code classeId} (élémentaire)
 * ; SURVEILLANCE : {@code cycles} (un ou plusieurs). {@code fin} facultative.
 */
public record AffectationRequete(
    @NotNull TypeAffectation type,
    @NotNull UUID anneeId,
    @NotNull UUID utilisateurId,
    UUID classeId,
    UUID matiereId,
    @Size(max = 3) Set<@NotNull CodeCycle> cycles,
    @NotNull LocalDate debut,
    LocalDate fin) {}
