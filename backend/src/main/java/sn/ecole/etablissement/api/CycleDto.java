package sn.ecole.etablissement.api;

import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.etablissement.ModeNotation;
import sn.ecole.etablissement.TypePeriode;

public record CycleDto(
    UUID id,
    Integer version,
    CodeCycle code,
    String libelle,
    TypePeriode typePeriode,
    ModeNotation modeNotation,
    int baseMoyenne) {}
