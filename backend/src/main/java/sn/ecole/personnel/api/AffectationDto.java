package sn.ecole.personnel.api;

import java.time.LocalDate;
import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.personnel.domain.TypeAffectation;

/**
 * Affectation et ses libellés. {@code fin} : première date où elle ne vaut plus (nulle : sans fin
 * prévue) ; {@code enCours} : en vigueur aujourd'hui.
 */
public record AffectationDto(
    UUID id,
    Integer version,
    UUID anneeId,
    UUID utilisateurId,
    TypeAffectation type,
    UUID classeId,
    String classe,
    UUID matiereId,
    String matiere,
    CodeCycle codeCycle,
    LocalDate debut,
    LocalDate fin,
    boolean enCours) {}
