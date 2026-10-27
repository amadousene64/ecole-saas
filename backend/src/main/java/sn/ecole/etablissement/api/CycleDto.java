package sn.ecole.etablissement.api;

import java.util.UUID;
import sn.ecole.etablissement.domain.CodeCycle;
import sn.ecole.etablissement.domain.ModeNotation;
import sn.ecole.etablissement.domain.TypePeriode;

public record CycleDto(
    UUID id,
    Integer version,
    CodeCycle code,
    String libelle,
    TypePeriode typePeriode,
    ModeNotation modeNotation,
    int baseMoyenne) {}
